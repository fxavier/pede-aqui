package com.delivery.payment.api;

import com.delivery.payment.entity.Payment;
import com.delivery.payment.entity.PaymentStatus;
import com.delivery.payment.entity.RefundStatus;
import com.delivery.payment.repository.PaymentRepository;
import com.delivery.payment.repository.RefundRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only payment facts for the settlement ledger (LAC-33, W6-B): an order's payment (amount, captured or not,
 * capture instant) and its refunds (amount, status, approval instant), so settlement never reaches into payment's
 * repositories or entities. Tenant-scoped; statuses are returned as names.
 */
@Component
public class PaymentSettlementFacts {
    /** The order's payment; {@code captured} = money was taken (CONFIRMED, PARTIALLY_REFUNDED, FULLY_REFUNDED). */
    public record PaymentFacts(UUID paymentId, UUID orderId, BigDecimal amount, boolean captured, Instant confirmedAt) {}

    /** One refund; {@code approvedAt} is set only for approved (REFUNDED) refunds. */
    public record RefundFacts(UUID refundId, UUID paymentId, BigDecimal amount, String status, Instant approvedAt) {
        public boolean approved() { return approvedAt != null; }
    }

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;

    public PaymentSettlementFacts(PaymentRepository paymentRepository, RefundRepository refundRepository) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
    }

    /** The order's payment, if any (one payment per order today). */
    @Transactional(readOnly = true)
    public Optional<PaymentFacts> paymentOfOrder(UUID tenantId, UUID orderId) {
        return paymentRepository.findByTenantIdAndOrderId(tenantId, orderId).map(PaymentSettlementFacts::toFacts);
    }

    /** The payment by id (tenant-scoped). */
    @Transactional(readOnly = true)
    public Optional<PaymentFacts> payment(UUID tenantId, UUID paymentId) {
        return paymentRepository.findByTenantIdAndId(tenantId, paymentId).map(PaymentSettlementFacts::toFacts);
    }

    /** Every refund of the payment, in a stable order (by id). */
    @Transactional(readOnly = true)
    public List<RefundFacts> refundsOfPayment(UUID tenantId, UUID paymentId) {
        return refundRepository.findByTenantIdAndPaymentId(tenantId, paymentId).stream()
                .map(refund -> new RefundFacts(refund.getId(), refund.getPaymentId(), refund.getAmount(), refund.getStatus().name(),
                        refund.getStatus() == RefundStatus.REFUNDED ? refund.getUpdatedAt() : null))
                .sorted(Comparator.comparing(RefundFacts::refundId))
                .toList();
    }

    private static PaymentFacts toFacts(Payment payment) {
        boolean captured = PaymentStatus.CAPTURED.contains(payment.getStatus());
        return new PaymentFacts(payment.getId(), payment.getOrderId(), payment.getAmount(), captured, payment.getConfirmedAt());
    }
}
