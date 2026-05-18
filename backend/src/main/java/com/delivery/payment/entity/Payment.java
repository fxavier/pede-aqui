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
    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public PaymentStatus getStatus() { return status; }
}
