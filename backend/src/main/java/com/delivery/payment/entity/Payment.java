package com.delivery.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Tracks a local/mock payment for an order. */
@Entity
@Table(name = "payments")
public class Payment {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "order_id", nullable = false)
    private UUID orderId;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(nullable = false)
    private String provider;
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;
    @Column(name = "confirmed_at")
    private Instant confirmedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Payment() {}

    public Payment(UUID id, UUID tenantId, UUID orderId, BigDecimal amount, String idempotencyKey) {
        this.id = id;
        this.tenantId = tenantId;
        this.orderId = orderId;
        this.amount = amount;
        this.provider = "LOCAL_MOCK";
        this.idempotencyKey = idempotencyKey;
        this.status = PaymentStatus.PENDING_CONFIRMATION;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void confirm() { this.status = PaymentStatus.CONFIRMED; this.confirmedAt = Instant.now(); this.updatedAt = confirmedAt; }
    /** Financial state after a refund approval: approved refunds cover part of the amount. */
    public void markPartiallyRefunded() { this.status = PaymentStatus.PARTIALLY_REFUNDED; this.updatedAt = Instant.now(); }
    /** Financial state after a refund approval: approved refunds cover the full amount. */
    public void markFullyRefunded() { this.status = PaymentStatus.FULLY_REFUNDED; this.updatedAt = Instant.now(); }
    /** Still awaiting confirmation (nothing captured yet). */
    public boolean isPendingConfirmation() { return status == PaymentStatus.PENDING_CONFIRMATION; }
    /** Money was captured ({@link PaymentStatus#CAPTURED}), whatever has been refunded since. */
    public boolean isCaptured() { return PaymentStatus.CAPTURED.contains(status); }
    /** Status name for logs/read models of other modules (they do not depend on the internal enum). */
    public String statusName() { return status.name(); }
    /** Pending payment abandoned (W4-A PaymentExpiryJob): nothing was captured, so nothing is refundable. */
    public void markCancelled() { this.status = PaymentStatus.CANCELLED; this.updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    /** When the payment was confirmed; null while pending (and for rows confirmed before the column was filled). */
    public Instant getConfirmedAt() { return confirmedAt; }
    public UUID getTenantId() { return tenantId; }
    public UUID getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public PaymentStatus getStatus() { return status; }
}
