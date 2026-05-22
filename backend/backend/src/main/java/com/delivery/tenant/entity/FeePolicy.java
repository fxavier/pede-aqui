package com.delivery.tenant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Stores tenant-level fees, commission, taxes, and cancellation policy rules. */
@Entity
@Table(name = "fee_policies")
public class FeePolicy {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;
    @Column(name = "delivery_fee", nullable = false)
    private BigDecimal deliveryFee;
    @Column(name = "service_fee", nullable = false)
    private BigDecimal serviceFee;
    @Column(name = "tax_rate", nullable = false)
    private BigDecimal taxRate;
    @Column(name = "commission_rate", nullable = false)
    private BigDecimal commissionRate;
    @Column(name = "cancellation_policy", nullable = false)
    private String cancellationPolicy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FeePolicy() {}

    public FeePolicy(UUID id, UUID tenantId, BigDecimal deliveryFee, BigDecimal serviceFee, BigDecimal taxRate, BigDecimal commissionRate, String cancellationPolicy) {
        this.id = id;
        this.tenantId = tenantId;
        this.deliveryFee = deliveryFee;
        this.serviceFee = serviceFee;
        this.taxRate = taxRate;
        this.commissionRate = commissionRate;
        this.cancellationPolicy = cancellationPolicy;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(BigDecimal deliveryFee, BigDecimal serviceFee, BigDecimal taxRate, BigDecimal commissionRate, String cancellationPolicy) {
        this.deliveryFee = deliveryFee;
        this.serviceFee = serviceFee;
        this.taxRate = taxRate;
        this.commissionRate = commissionRate;
        this.cancellationPolicy = cancellationPolicy;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public BigDecimal getServiceFee() { return serviceFee; }
    public BigDecimal getTaxRate() { return taxRate; }
    public BigDecimal getCommissionRate() { return commissionRate; }
    public String getCancellationPolicy() { return cancellationPolicy; }
}
