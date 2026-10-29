package com.delivery.order.service;

import com.delivery.order.entity.Order;
import com.delivery.order.entity.StockStatus;
import com.delivery.order.repository.OrderRepository;
import com.delivery.order.repository.OrderRepository.StockReconciliationCandidate;
import com.delivery.payment.repository.PaymentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Safety net for the stock lifecycle (LAC-23, LAC-46): finds orders whose stock is still {@code RESERVED} although
 * the order is over, and closes it through {@link OrderStockService} exactly as the online paths would have —
 * release when the order died before pickup, commit when the goods left the store. Same classification as the V043
 * data fix; the online paths (INC-07) should leave nothing to do, so every fixed order is logged at WARN as drift.
 *
 * <p>Each order is handled in its own transaction and follows the lock order rule: the order's payment row is locked
 * first ({@code FOR UPDATE SKIP LOCKED} — a payment held by a request in flight is skipped until the next run), then
 * the order and its delivery status are re-read and re-classified, then inventory rows are locked by
 * {@link OrderStockService} in SKU order. Idempotent: {@code orders.stock_status} only moves away from RESERVED once,
 * and a concurrent change of the order loses on its {@code @Version} (counted as failed, retried next run).
 */
@Service
public class ReservationReconciler {
    private static final Logger log = LoggerFactory.getLogger(ReservationReconciler.class);

    /** Delivery statuses after pickup — must equal {@code DeliveryStatus.isGoodsLeftTheStore()} (pinned by a test). */
    static final Set<String> DELIVERY_GOODS_LEFT_THE_STORE =
            Set.of("PICKED_UP", "ON_ROUTE_TO_CUSTOMER", "ARRIVED_AT_CUSTOMER", "DELIVERED", "FAILED_DELIVERY");
    /** Live delivery statuses with a courier engaged before pickup: a REFUNDED order with one is left for ops (V043). */
    static final Set<String> DELIVERY_COURIER_ENGAGED_BEFORE_PICKUP =
            Set.of("ASSIGNED", "ACCEPTED", "ARRIVED_AT_VENDOR", "REASSIGNED");
    static final Set<String> ORDER_PAST_PICKUP = Set.of("PICKED_UP", "DELIVERING", "DELIVERED");
    static final Set<String> ORDER_DEAD_BEFORE_PICKUP = Set.of("CANCELLED", "REJECTED_BY_VENDOR");

    /** What to do with one order's reservation. */
    public enum Action { COMMIT, RELEASE, NEEDS_ATTENTION, NONE }

    /** Outcome counters of one reconciliation run. */
    public record Result(int examined, int released, int committed, int skippedBusy, int needsAttention, int failed) {
        @Override
        public String toString() {
            return "examined=" + examined + " released=" + released + " committed=" + committed
                    + " skippedBusy=" + skippedBusy + " needsAttention=" + needsAttention + " failed=" + failed;
        }
    }

    private enum Outcome { RELEASED, COMMITTED, SKIPPED_BUSY, NEEDS_ATTENTION, ALREADY_SETTLED }

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderStockService orderStockService;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;
    private final Duration gracePeriod;
    private final int batchSize;

    public ReservationReconciler(OrderRepository orderRepository, PaymentRepository paymentRepository,
                                 OrderStockService orderStockService, PlatformTransactionManager transactionManager,
                                 MeterRegistry meterRegistry,
                                 @Value("${app.jobs.reservation-reconciler.grace-period:PT10M}") Duration gracePeriod,
                                 @Value("${app.jobs.reservation-reconciler.batch-size:200}") int batchSize) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.orderStockService = orderStockService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.meterRegistry = meterRegistry;
        this.gracePeriod = gracePeriod;
        this.batchSize = batchSize;
    }

    /**
     * Pure classification (V043 rule) of an order whose stock is RESERVED: COMMIT once the goods left the store,
     * RELEASE when the order died before pickup, NEEDS_ATTENTION for a refunded order whose courier is still engaged
     * before pickup (the refund path retires such deliveries, so this is an anomaly for ops), NONE for live orders.
     */
    public static Action classify(String orderStatus, String deliveryStatus) {
        if ((deliveryStatus != null && DELIVERY_GOODS_LEFT_THE_STORE.contains(deliveryStatus)) || ORDER_PAST_PICKUP.contains(orderStatus)) {
            return Action.COMMIT;
        }
        if (ORDER_DEAD_BEFORE_PICKUP.contains(orderStatus)) {
            return Action.RELEASE;
        }
        if ("REFUNDED".equals(orderStatus)) {
            return deliveryStatus != null && DELIVERY_COURIER_ENGAGED_BEFORE_PICKUP.contains(deliveryStatus)
                    ? Action.NEEDS_ATTENTION : Action.RELEASE;
        }
        return Action.NONE;
    }

    /** Runs one bounded batch (oldest first) and returns the counts; also exported as stock_reconciler_orders_total{outcome}. */
    public Result reconcile() {
        List<StockReconciliationCandidate> candidates =
                orderRepository.findStockReconciliationCandidates(Instant.now().minus(gracePeriod), batchSize);
        int released = 0, committed = 0, busy = 0, attention = 0, failed = 0;
        for (StockReconciliationCandidate candidate : candidates) {
            Outcome outcome;
            try {
                outcome = transactionTemplate.execute(status -> reconcileOne(candidate.getTenantId(), candidate.getOrderId()));
            } catch (RuntimeException ex) {
                failed++;
                count("failed");
                log.warn("Stock reconciliation failed for order {} (retried next run): {}", candidate.getOrderId(), ex.toString());
                continue;
            }
            switch (outcome) {
                case RELEASED -> released++;
                case COMMITTED -> committed++;
                case SKIPPED_BUSY -> busy++;
                case NEEDS_ATTENTION -> attention++;
                case ALREADY_SETTLED -> { }
            }
            count(outcome.name().toLowerCase());
        }
        return new Result(candidates.size(), released, committed, busy, attention, failed);
    }

    private Outcome reconcileOne(UUID tenantId, UUID orderId) {
        // 1. Lock order rule: payment row first. Skip (not wait) when a request holds it.
        if (paymentRepository.lockIdsByTenantIdAndOrderIdSkipLocked(tenantId, orderId).isEmpty()
                && paymentRepository.existsByTenantIdAndOrderId(tenantId, orderId)) {
            return Outcome.SKIPPED_BUSY;
        }
        // 2. Re-read the order and its delivery under the lock and re-classify (the candidate list is a snapshot).
        Optional<Order> found = orderRepository.findByTenantIdAndId(tenantId, orderId);
        if (found.isEmpty() || found.get().getStockStatus() != StockStatus.RESERVED) {
            return Outcome.ALREADY_SETTLED;
        }
        Order order = found.get();
        String deliveryStatus = orderRepository.findDeliveryStatus(tenantId, orderId).orElse(null);
        Action action = classify(order.getStatus().name(), deliveryStatus);
        // 3. Inventory last (OrderStockService locks inventory rows in SKU order).
        switch (action) {
            case RELEASE -> {
                orderStockService.release(order);
                log.warn("Stock drift fixed: released reservation of order {} (tenant {}, status {}, delivery {})",
                        orderId, tenantId, order.getStatus(), deliveryStatus);
                return Outcome.RELEASED;
            }
            case COMMIT -> {
                orderStockService.commit(order);
                log.warn("Stock drift fixed: committed reservation of order {} (tenant {}, status {}, delivery {})",
                        orderId, tenantId, order.getStatus(), deliveryStatus);
                return Outcome.COMMITTED;
            }
            case NEEDS_ATTENTION -> {
                log.warn("Stock reservation of refunded order {} (tenant {}) left RESERVED: delivery {} still engaged before pickup; needs ops review",
                        orderId, tenantId, deliveryStatus);
                return Outcome.NEEDS_ATTENTION;
            }
            default -> {
                return Outcome.ALREADY_SETTLED; // order moved back to a live state since the snapshot: nothing to do
            }
        }
    }

    private void count(String outcome) {
        meterRegistry.counter("stock.reconciler.orders", "outcome", outcome).increment();
    }
}
