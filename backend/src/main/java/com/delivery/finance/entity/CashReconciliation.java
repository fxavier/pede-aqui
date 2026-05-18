package com.delivery.finance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Tracks cash-on-delivery money reconciliation records. */
@Entity
@Table(name = "cash_reconciliations")
public class CashReconciliation {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "courier_id")
    private UUID courierId;
    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;
    @Column(name = "order_id", nullable = false)
    private UUID orderId;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(nullable = false)
    private String status;
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
    @Column(name = "reconciled_at")
    private Instant reconciledAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected CashReconciliation() {}

    public CashReconciliation(UUID id, UUID tenantId, UUID courierId, UUID deliveryId, UUID orderId, BigDecimal amount, String status) {
        this.id = id;
        this.tenantId = tenantId;
        this.courierId = courierId;
        this.deliveryId = deliveryId;
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
        this.recordedAt = Instant.now();
        this.createdAt = this.recordedAt;
        this.updatedAt = this.recordedAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getCourierId() { return courierId; }
    public UUID getDeliveryId() { return deliveryId; }
    public UUID getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public Instant getRecordedAt() { return recordedAt; }
}
