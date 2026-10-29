package com.delivery.order.service;

import com.delivery.common.service.AuditActions;
import com.delivery.common.service.AuditLogService;
import com.delivery.delivery.api.DeliveryRetirementService;
import com.delivery.notification.service.NotificationService;
import com.delivery.order.api.OrderStateMachine;
import com.delivery.order.entity.CancellationReason;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.repository.OrderRepository;
import com.delivery.order.repository.OrderRepository.StockReconciliationCandidate;
import com.delivery.payment.dto.RefundResponse;
import com.delivery.payment.entity.Payment;
import com.delivery.payment.repository.PaymentRepository;
import com.delivery.payment.service.PaymentService;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Order lifecycle timeouts (LAC-25, W4-A), run by PaymentExpiryJob and AcceptanceTimeoutJob:
 * <ul>
 *   <li><b>payment expiry</b> — an order still {@code PAYMENT_PENDING} {@code app.orders.payment-timeout} (default
 *       20 min) after checkout becomes {@code CANCELLED} with reason {@code PAYMENT_EXPIRED}; its pending payment
 *       becomes {@code CANCELLED}, its delivery is retired and its stock reservation released. Nothing was captured,
 *       so there is no refund.</li>
 *   <li><b>acceptance timeout</b> — an order still {@code PAYMENT_CONFIRMED} {@code app.orders.acceptance-timeout}
 *       (default 10 min) after its payment was confirmed becomes {@code CANCELLED} with reason {@code VENDOR_TIMEOUT},
 *       exactly like a seller rejection: delivery retired, stock released, the eligible remainder of the payment
 *       refunded (key {@code vendor-timeout-<orderId>}), the customer notified, audited.</li>
 * </ul>
 *
 * <p>Work-queue pattern (CLAUDE.md, Scheduled jobs): a bounded read-only candidate list (oldest first), then each
 * order in <b>its own transaction</b> that follows the lock order rule — the order's payment row is claimed with
 * {@code FOR UPDATE SKIP LOCKED} (a payment held by a user request is skipped and retried on the next run), the
 * order is re-read and re-checked under that lock, then the delivery/dispatch rows are retired and finally inventory
 * is touched. A user action that changes the same order concurrently either holds the payment lock (seller rejection,
 * sales cancel/refund/override: the job skips) or loses/wins on the order's {@code @Version} (vendor accept, payment
 * confirmation): exactly one of them commits, the other rolls back entirely — no double refund, release or
 * notification. Every status change goes through {@link OrderStateMachine} (history + OrderStatusChanged).
 *
 * <p>Metrics: {@code order_timeout_orders_total{job,outcome=cancelled|skipped_busy|already_settled|needs_attention|failed}}
 * plus the JobRunner metrics of each job.
 */
@Service
public class OrderTimeoutService {
    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutService.class);
    public static final String PAYMENT_EXPIRY = "payment-expiry";
    public static final String ACCEPTANCE_TIMEOUT = "acceptance-timeout";
    /** Idempotency key prefix of the acceptance-timeout refund. */
    public static final String VENDOR_TIMEOUT_REFUND_KEY_PREFIX = "vendor-timeout-";

    /** Outcome of one order. */
    public enum Outcome { CANCELLED, SKIPPED_BUSY, ALREADY_SETTLED, NEEDS_ATTENTION }

    /** Outcome counters of one run. */
    public record Result(int examined, int cancelled, int skippedBusy, int alreadySettled, int needsAttention, int failed) {
        @Override
        public String toString() {
            return "examined=" + examined + " cancelled=" + cancelled + " skippedBusy=" + skippedBusy
                    + " alreadySettled=" + alreadySettled + " needsAttention=" + needsAttention + " failed=" + failed;
        }
    }

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final OrderStateMachine stateMachine;
    private final OrderStockService orderStockService;
    private final DeliveryRetirementService deliveryRetirementService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;
    private final Duration paymentTimeout;
    private final Duration acceptanceTimeout;
    private final int batchSize;

    public OrderTimeoutService(OrderRepository orderRepository, PaymentRepository paymentRepository, PaymentService paymentService,
                               OrderStateMachine stateMachine, OrderStockService orderStockService,
                               DeliveryRetirementService deliveryRetirementService, NotificationService notificationService,
                               AuditLogService auditLogService, PlatformTransactionManager transactionManager, MeterRegistry meterRegistry,
                               @Value("${app.orders.payment-timeout:PT20M}") Duration paymentTimeout,
                               @Value("${app.orders.acceptance-timeout:PT10M}") Duration acceptanceTimeout,
                               @Value("${app.orders.timeout-batch-size:200}") int batchSize) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.stateMachine = stateMachine;
        this.orderStockService = orderStockService;
        this.deliveryRetirementService = deliveryRetirementService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.meterRegistry = meterRegistry;
        this.paymentTimeout = paymentTimeout;
        this.acceptanceTimeout = acceptanceTimeout;
        this.batchSize = batchSize;
    }

    /** Cancels one bounded batch of orders whose payment stayed pending past the payment timeout. */
    public Result expirePendingPayments() {
        Instant cutoff = Instant.now().minus(paymentTimeout);
        return runBatch(PAYMENT_EXPIRY, orderRepository.findPaymentExpiryCandidates(cutoff, batchSize),
                (tenantId, orderId) -> expirePayment(tenantId, orderId, cutoff));
    }

    /** Cancels (and refunds) one bounded batch of paid orders the vendor did not accept within the acceptance timeout. */
    public Result cancelUnacceptedOrders() {
        Instant cutoff = Instant.now().minus(acceptanceTimeout);
        return runBatch(ACCEPTANCE_TIMEOUT, orderRepository.findAcceptanceTimeoutCandidates(cutoff, batchSize),
                (tenantId, orderId) -> cancelUnaccepted(tenantId, orderId, cutoff));
    }

    private Result runBatch(String job, List<StockReconciliationCandidate> candidates, BiFunction<UUID, UUID, Outcome> handler) {
        int cancelled = 0, busy = 0, settled = 0, attention = 0, failed = 0;
        for (StockReconciliationCandidate candidate : candidates) {
            Outcome outcome;
            try {
                outcome = transactionTemplate.execute(status -> handler.apply(candidate.getTenantId(), candidate.getOrderId()));
            } catch (RuntimeException ex) {
                // e.g. a concurrent user action won on the order's @Version: everything of this order rolled back.
                failed++;
                count(job, "failed");
                log.warn("Order timeout {} failed for order {} (retried next run): {}", job, candidate.getOrderId(), ex.toString());
                continue;
            }
            switch (outcome) {
                case CANCELLED -> cancelled++;
                case SKIPPED_BUSY -> busy++;
                case ALREADY_SETTLED -> settled++;
                case NEEDS_ATTENTION -> attention++;
            }
            count(job, outcome.name().toLowerCase());
        }
        return new Result(candidates.size(), cancelled, busy, settled, attention, failed);
    }

    /** One order of the payment-expiry run, inside its own transaction (package-visible for tests). */
    Outcome expirePayment(UUID tenantId, UUID orderId, Instant cutoff) {
        // 1. Lock order rule: payment row first; skip (do not wait) when a request holds it.
        List<UUID> lockedPayments = paymentRepository.lockIdsByTenantIdAndOrderIdSkipLocked(tenantId, orderId);
        if (lockedPayments.isEmpty() && paymentRepository.existsByTenantIdAndOrderId(tenantId, orderId)) {
            return Outcome.SKIPPED_BUSY;
        }
        // 2. Re-read and re-check the order under the lock (the candidate list is a snapshot).
        Optional<Order> found = orderRepository.findByTenantIdAndId(tenantId, orderId);
        if (found.isEmpty() || found.get().getStatus() != OrderStatus.PAYMENT_PENDING || !found.get().getCreatedAt().isBefore(cutoff)) {
            return Outcome.ALREADY_SETTLED;
        }
        Order order = found.get();
        stateMachine.cancel(order, CancellationReason.PAYMENT_EXPIRED, null);
        if (!lockedPayments.isEmpty()) {
            paymentService.expirePending(tenantId, lockedPayments.get(0));
        }
        // 3. Delivery/dispatch rows, then inventory.
        deliveryRetirementService.retireBeforePickup(tenantId, orderId, "payment expired");
        orderStockService.release(order);
        auditLogService.logForTenant(tenantId, AuditActions.ORDER_CANCELLED_PAYMENT_EXPIRED, "order", orderId.toString(),
                order.getReference(), "SUCCESS", Map.of("timeout", paymentTimeout.toString()));
        return Outcome.CANCELLED;
    }

    /** One order of the acceptance-timeout run, inside its own transaction (package-visible for tests). */
    Outcome cancelUnaccepted(UUID tenantId, UUID orderId, Instant cutoff) {
        // 1. Lock order rule: payment row first; skip (do not wait) when a request holds it.
        List<UUID> lockedPayments = paymentRepository.lockIdsByTenantIdAndOrderIdSkipLocked(tenantId, orderId);
        if (lockedPayments.isEmpty()) {
            if (paymentRepository.existsByTenantIdAndOrderId(tenantId, orderId)) {
                return Outcome.SKIPPED_BUSY;
            }
            log.warn("Order {} (tenant {}) is PAYMENT_CONFIRMED without a payment row; acceptance timeout needs ops review", orderId, tenantId);
            return Outcome.NEEDS_ATTENTION;
        }
        Payment payment = paymentRepository.findByTenantIdAndIdForUpdate(tenantId, lockedPayments.get(0)).orElseThrow();
        // 2. Re-read and re-check the order under the lock.
        Optional<Order> found = orderRepository.findByTenantIdAndId(tenantId, orderId);
        if (found.isEmpty() || found.get().getStatus() != OrderStatus.PAYMENT_CONFIRMED) {
            return Outcome.ALREADY_SETTLED;
        }
        Order order = found.get();
        Instant confirmedAt = payment.getConfirmedAt() != null ? payment.getConfirmedAt() : order.getUpdatedAt();
        if (!confirmedAt.isBefore(cutoff)) {
            return Outcome.ALREADY_SETTLED;
        }
        stateMachine.cancel(order, CancellationReason.VENDOR_TIMEOUT, null);
        // 3. Delivery/dispatch rows, then inventory, then the refund (re-locks the same payment row: re-entrant).
        deliveryRetirementService.retireBeforePickup(tenantId, orderId, "vendor did not accept in time");
        orderStockService.release(order);
        BigDecimal refunded = paymentService.requestRemainingRefundForTenant(tenantId, payment.getId(),
                        "Vendor acceptance timeout", VENDOR_TIMEOUT_REFUND_KEY_PREFIX + orderId)
                .map(RefundResponse::amount)
                .orElse(BigDecimal.ZERO);
        notificationService.createForTenant(tenantId, order.getCustomerId(), "CUSTOMER", "order_cancelled_vendor_timeout", "Order Cancelled",
                String.format("Your order %s was cancelled because the store did not accept it in time. The amount paid will be refunded.",
                        order.getReference()),
                order.getReference());
        auditLogService.logForTenant(tenantId, AuditActions.ORDER_CANCELLED_VENDOR_TIMEOUT, "order", orderId.toString(),
                "refund_remaining=" + refunded.setScale(2, RoundingMode.HALF_UP).toPlainString(), "SUCCESS",
                Map.of("timeout", acceptanceTimeout.toString()));
        return Outcome.CANCELLED;
    }

    private void count(String job, String outcome) {
        meterRegistry.counter("order.timeout.orders", "job", job, "outcome", outcome).increment();
    }
}
