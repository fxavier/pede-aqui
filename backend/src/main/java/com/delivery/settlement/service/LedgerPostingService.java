package com.delivery.settlement.service;

import com.delivery.order.api.OrderSettlementFacts;
import com.delivery.order.api.OrderSettlementFacts.OrderFinancials;
import com.delivery.payment.api.PaymentSettlementFacts;
import com.delivery.payment.api.PaymentSettlementFacts.PaymentFacts;
import com.delivery.payment.api.PaymentSettlementFacts.RefundFacts;
import com.delivery.settlement.config.SettlementProperties;
import com.delivery.settlement.entity.LedgerTransactionType;
import com.delivery.settlement.repository.LedgerRepository;
import com.delivery.settlement.repository.SettlementCommissionRepository;
import com.delivery.settlement.repository.SettlementCommissionRepository.ResolvedRate;
import com.delivery.settlement.service.PostingRules.Line;
import com.delivery.settlement.service.PostingRules.Recognition;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the settlement ledger (LAC-33/34) from business facts, idempotently: each posting has a fixed idempotency key
 * ({@link LedgerTransactionType#key}) that is UNIQUE per tenant, so a redelivered event changes nothing. Postings
 * read the committed order/payment facts rather than trusting event order, and first make sure the facts they build
 * on are posted (capture before anything else, delivery before a post-delivery refund) — always in the order
 * capture → delivery → refund/fee, which is also the order in which their keys are locked (no deadlocks).
 * Runs inside the caller's transaction (the {@code @ApplicationModuleListener}'s own one).
 */
@Service
public class LedgerPostingService {
    private static final Logger log = LoggerFactory.getLogger(LedgerPostingService.class);
    static final String CANCELLED_WITH_FEE = "CUSTOMER_CANCELLED_WITH_FEE";

    private final LedgerRepository ledger;
    private final LedgerAccounts accounts;
    private final SettlementCommissionRepository commissions;
    private final OrderSettlementFacts orderFacts;
    private final PaymentSettlementFacts paymentFacts;
    private final SettlementProperties properties;
    private final SettlementMetrics metrics;

    public LedgerPostingService(LedgerRepository ledger, LedgerAccounts accounts, SettlementCommissionRepository commissions,
                                OrderSettlementFacts orderFacts, PaymentSettlementFacts paymentFacts,
                                SettlementProperties properties, SettlementMetrics metrics) {
        this.ledger = ledger;
        this.accounts = accounts;
        this.commissions = commissions;
        this.orderFacts = orderFacts;
        this.paymentFacts = paymentFacts;
        this.properties = properties;
        this.metrics = metrics;
    }

    /** Payment captured: Dr cash clearing / Cr customer funds held. Returns true when posted now. */
    @Transactional
    public boolean recordPaymentCaptured(UUID tenantId, UUID paymentId) {
        Optional<PaymentFacts> payment = paymentFacts.payment(tenantId, paymentId);
        if (payment.isEmpty() || !payment.get().captured()) {
            return skip(LedgerTransactionType.PAYMENT_CAPTURED, "payment " + paymentId + " not captured");
        }
        return ensureCaptured(tenantId, payment.get());
    }

    /** Order delivered: recognises merchant payable, commission and fees (+ the commissions row). */
    @Transactional
    public boolean recordOrderDelivered(UUID tenantId, UUID orderId) {
        Optional<OrderFinancials> order = orderFacts.financials(tenantId, orderId);
        if (order.isEmpty() || order.get().deliveredAt() == null) {
            return skip(LedgerTransactionType.ORDER_DELIVERED, "order " + orderId + " not delivered");
        }
        Optional<PaymentFacts> payment = paymentFacts.paymentOfOrder(tenantId, orderId);
        if (payment.isEmpty() || !payment.get().captured()) {
            return skip(LedgerTransactionType.ORDER_DELIVERED, "order " + orderId + " has no captured payment");
        }
        ensureCaptured(tenantId, payment.get());
        return postDelivery(order.get(), payment.get());
    }

    /**
     * Refund approved. Approved after the order's DELIVERED instant → proportional reversal of the delivery
     * recognition (the delivery is posted first if its event has not been processed yet); otherwise customer funds
     * held → refunds payable.
     */
    @Transactional
    public boolean recordRefundApproved(UUID tenantId, UUID refundId, UUID paymentId, UUID orderId, BigDecimal amount, Instant approvedAt) {
        Optional<PaymentFacts> payment = paymentFacts.payment(tenantId, paymentId);
        if (payment.isEmpty() || !payment.get().captured()) {
            return skip(LedgerTransactionType.REFUND_BEFORE_DELIVERY, "refund " + refundId + " on a payment not captured");
        }
        ensureCaptured(tenantId, payment.get());
        Optional<OrderFinancials> order = orderFacts.financials(tenantId, orderId);
        Instant deliveredAt = order.map(OrderFinancials::deliveredAt).orElse(null);
        if (deliveredAt != null && deliveredAt.isBefore(approvedAt)) {
            postDelivery(order.get(), payment.get());
            List<Line> delivery = ledger.linesOf(tenantId, LedgerTransactionType.ORDER_DELIVERED.key(orderId));
            if (!delivery.isEmpty()) {
                return post(tenantId, LedgerTransactionType.REFUND_AFTER_DELIVERY, refundId, "REFUND", refundId, orderId,
                        order.get().vendorId(), approvedAt, "Refund approved after delivery: proportional reversal",
                        PostingRules.refundAfterDelivery(delivery, amount)).isPresent();
            }
            log.warn("Ledger: order {} delivered but its delivery recognition is empty; refund {} posted against funds held", orderId, refundId);
        }
        return post(tenantId, LedgerTransactionType.REFUND_BEFORE_DELIVERY, refundId, "REFUND", refundId, orderId,
                order.map(OrderFinancials::vendorId).orElse(null), approvedAt, "Refund approved before delivery",
                PostingRules.refundBeforeDelivery(amount)).isPresent();
    }

    /** Order cancelled: only a customer cancellation with a withheld fee (LAC-26) moves money in the ledger. */
    @Transactional
    public boolean recordCancellation(UUID tenantId, UUID orderId) {
        Optional<OrderFinancials> found = orderFacts.financials(tenantId, orderId);
        if (found.isEmpty() || !CANCELLED_WITH_FEE.equals(found.get().cancellationReason())
                || found.get().retainedCancellationFee().signum() <= 0) {
            return false; // other cancellations: refunds carry the money movements
        }
        OrderFinancials order = found.get();
        Optional<PaymentFacts> payment = paymentFacts.paymentOfOrder(tenantId, orderId);
        if (payment.isEmpty() || !payment.get().captured()) {
            return skip(LedgerTransactionType.CANCELLATION_FEE_RETAINED, "order " + orderId + " has no captured payment");
        }
        ensureCaptured(tenantId, payment.get());
        Instant at = order.cancelledAt() != null ? order.cancelledAt() : Instant.now();
        ResolvedRate rate = rateAt(tenantId, order.vendorId(), at);
        Recognition recognition = PostingRules.cancellationFeeRetained(order.vendorId(), order.retainedCancellationFee(),
                rate.rate(), properties.getPlatformVatRate());
        Optional<UUID> posted = post(tenantId, LedgerTransactionType.CANCELLATION_FEE_RETAINED, orderId, "ORDER", orderId, orderId,
                order.vendorId(), at, "Customer cancellation fee retained: merchant payable and commission", recognition.lines());
        posted.ifPresent(txId -> commissions.insertAccrued(tenantId, orderId, order.vendorId(), recognition.commissionBasis(),
                recognition.commissionRate(), recognition.commissionAmount(), rate.planId(), "CANCELLATION_FEE", txId));
        return posted.isPresent();
    }

    private boolean ensureCaptured(UUID tenantId, PaymentFacts payment) {
        Instant at = payment.confirmedAt() != null ? payment.confirmedAt() : Instant.now();
        return post(tenantId, LedgerTransactionType.PAYMENT_CAPTURED, payment.paymentId(), "PAYMENT", payment.paymentId(),
                payment.orderId(), null, at, "Payment captured: customer funds held", PostingRules.paymentCaptured(payment.amount()))
                .isPresent();
    }

    private boolean postDelivery(OrderFinancials order, PaymentFacts payment) {
        UUID tenantId = order.tenantId();
        String key = LedgerTransactionType.ORDER_DELIVERED.key(order.orderId());
        if (ledger.exists(tenantId, key)) {
            metrics.posting(LedgerTransactionType.ORDER_DELIVERED.name(), "duplicate");
            return false;
        }
        Instant deliveredAt = order.deliveredAt();
        BigDecimal refundedBefore = paymentFacts.refundsOfPayment(tenantId, payment.paymentId()).stream()
                .filter(RefundFacts::approved)
                .filter(refund -> refund.approvedAt().isBefore(deliveredAt))
                .map(RefundFacts::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        ResolvedRate rate = rateAt(tenantId, order.vendorId(), deliveredAt);
        Recognition recognition = PostingRules.orderDelivered(new PostingRules.DeliveredInput(order.vendorId(), payment.amount(),
                order.subtotal(), order.fees(), order.discountTotal(), refundedBefore, rate.rate(), properties.getPlatformVatRate(),
                properties.getDiscountFundedBy() == SettlementProperties.DiscountFunding.PLATFORM));
        if (recognition.lines().isEmpty()) {
            return skip(LedgerTransactionType.ORDER_DELIVERED, "order " + order.orderId() + " fully refunded before delivery");
        }
        Optional<UUID> posted = post(tenantId, LedgerTransactionType.ORDER_DELIVERED, order.orderId(), "ORDER", order.orderId(),
                order.orderId(), order.vendorId(), deliveredAt, "Order delivered: merchant payable, commission and fees recognised",
                recognition.lines());
        posted.ifPresent(txId -> commissions.insertAccrued(tenantId, order.orderId(), order.vendorId(), recognition.commissionBasis(),
                recognition.commissionRate(), recognition.commissionAmount(), rate.planId(), "ORDER_DELIVERED", txId));
        return posted.isPresent();
    }

    private ResolvedRate rateAt(UUID tenantId, UUID vendorId, Instant at) {
        return commissions.resolve(tenantId, vendorId, at)
                .orElseGet(() -> new ResolvedRate(properties.getDefaultCommissionRate(), null, "CONFIG_DEFAULT"));
    }

    /** Inserts header + lines unless the tenant already has the key (then nothing is written). */
    private Optional<UUID> post(UUID tenantId, LedgerTransactionType type, UUID keySource, String sourceType, UUID sourceId,
                                UUID orderId, UUID vendorId, Instant occurredAt, String description, List<Line> lines) {
        String key = type.key(keySource);
        if (lines.isEmpty()) {
            metrics.posting(type.name(), "skipped");
            return Optional.empty();
        }
        // Resolve (and create) accounts in one global order before taking the key: see LedgerAccounts.
        Map<String, UUID> accountIds = new HashMap<>();
        lines.stream()
                .sorted(Comparator.comparing((Line line) -> line.account().ordinal())
                        .thenComparing(line -> String.valueOf(line.ownerId())))
                .forEach(line -> accountIds.computeIfAbsent(accountKey(line),
                        k -> accounts.idOf(tenantId, line.account(), line.ownerId())));
        Optional<UUID> txId = ledger.insertTransaction(tenantId, key, type, sourceType, sourceId, orderId, vendorId, occurredAt, description);
        if (txId.isEmpty()) {
            metrics.posting(type.name(), "duplicate");
            return Optional.empty();
        }
        for (Line line : lines) {
            BigDecimal amount = line.amount();
            ledger.insertEntry(tenantId, txId.get(), accountIds.get(accountKey(line)),
                    amount.signum() > 0 ? amount : BigDecimal.ZERO, amount.signum() < 0 ? amount.negate() : BigDecimal.ZERO);
        }
        metrics.posting(type.name(), "posted");
        log.info("Ledger {} posted (tenant {}, key {}, {} lines)", type, tenantId, key, lines.size());
        return txId;
    }

    private static String accountKey(Line line) {
        return line.account().name() + ":" + line.ownerId();
    }

    private boolean skip(LedgerTransactionType type, String why) {
        metrics.posting(type.name(), "skipped");
        log.warn("Ledger {} skipped: {}", type, why);
        return false;
    }
}
