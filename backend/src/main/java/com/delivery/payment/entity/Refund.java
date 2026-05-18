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

/** Tracks a total or partial refund request. */
@Entity
@Table(name = "refunds")
public class Refund {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;
    @Column(name = "order_id", nullable = false)
    private UUID orderId;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(nullable = false)
    private String reason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status;
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Refund() {}

    public Refund(UUID id, UUID tenantId, UUID paymentId, UUID orderId, BigDecimal amount, String reason, RefundStatus status, String idempotencyKey) {
        this.id = id;
        this.tenantId = tenantId;
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.amount = amount;
        this.reason = reason;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public String getReason() { return reason; }
    public RefundStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }

    public void approve() { this.status = RefundStatus.REFUNDED; this.updatedAt = Instant.now(); }
    public void reject() { this.status = RefundStatus.REJECTED; this.updatedAt = Instant.now(); }
}
