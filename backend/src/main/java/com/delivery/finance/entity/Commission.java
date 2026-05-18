package com.delivery.finance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Represents commission charges attributable to marketplace orders. */
@Entity
@Table(name = "commissions")
public class Commission {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "order_id", nullable = false)
    private UUID orderId;
    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;
    @Column(name = "basis_amount", nullable = false)
    private BigDecimal basisAmount;
    @Column(name = "commission_rate", nullable = false)
    private BigDecimal commissionRate;
    @Column(name = "commission_amount", nullable = false)
    private BigDecimal commissionAmount;
    @Column(nullable = false)
    private String status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Commission() {}

    public Commission(UUID id, UUID tenantId, UUID orderId, UUID vendorId, BigDecimal basisAmount, BigDecimal commissionRate, BigDecimal commissionAmount, String status) {
        this.id = id;
        this.tenantId = tenantId;
        this.orderId = orderId;
        this.vendorId = vendorId;
        this.basisAmount = basisAmount;
        this.commissionRate = commissionRate;
        this.commissionAmount = commissionAmount;
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getOrderId() { return orderId; }
    public UUID getVendorId() { return vendorId; }
    public BigDecimal getBasisAmount() { return basisAmount; }
    public BigDecimal getCommissionRate() { return commissionRate; }
    public BigDecimal getCommissionAmount() { return commissionAmount; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
