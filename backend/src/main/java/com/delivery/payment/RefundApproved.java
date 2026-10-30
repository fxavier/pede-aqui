package com.delivery.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Application event (LAC-33, W6-B): a refund was approved ({@code REQUESTED -> REFUNDED}; an administrative record —
 * no PSP payout yet). Published by {@code PaymentService.approveRefund} inside the approving transaction, only on the
 * first approval (a repeated approval publishes nothing), and delivered after commit through the JDBC event
 * publication registry (at-least-once — listeners must be idempotent; natural key {@code refundId}). Ids, amount and
 * instant only. See docs/architecture/EVENT-CATALOG.md.
 */
public record RefundApproved(UUID tenantId, UUID refundId, UUID paymentId, UUID orderId, BigDecimal amount,
                             String currency, Instant approvedAt) {
}
