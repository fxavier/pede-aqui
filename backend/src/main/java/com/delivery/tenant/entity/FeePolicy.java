package com.delivery.tenant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Stores tenant-level fees, commission, taxes, and cancellation policy rules. Read by the pricing engine (INC-13):
 * delivery_fee is the base delivery fee, service fee = service_fee (flat) + subtotal × service_fee_rate. tax_rate is the
 * standard VAT rate (16 % since V064); with tax_inclusive (default, W6-A) catalogue prices and fees already contain it and
 * VAT is extracted, otherwise it is added on top (subtotal × tax_rate, the pre-W6-A formula). max_delivery_distance_km
 * (null = no limit) caps the straight-line delivery distance at checkout unless the vendor sets its own.
 */
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
    @Column(name = "service_fee_rate", nullable = false)
    private BigDecimal serviceFeeRate = BigDecimal.ZERO;
    @Column(name = "tax_rate", nullable = false)
    private BigDecimal taxRate;
    @Column(name = "tax_inclusive", nullable = false)
    private boolean taxInclusive = true;
    @Column(name = "max_delivery_distance_km")
    private BigDecimal maxDeliveryDistanceKm;
    @Column(name = "commission_rate", nullable = false)
    private BigDecimal commissionRate;
    @Column(name = "cancellation_policy", nullable = false)
    private String cancellationPolicy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    /** Optimistic lock; also the rule version recorded on order pricing quotes. */
    @Version
    private long version;

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

    /** Sets the service fee percentage (fraction of the subtotal, 0.05 = 5 %). */
    public void changeServiceFeeRate(BigDecimal serviceFeeRate) {
        this.serviceFeeRate = serviceFeeRate;
        this.updatedAt = Instant.now();
    }

    /** Sets whether catalogue prices and fees include VAT (true, W6-A default) or VAT is added on top (false). */
    public void changeTaxInclusive(boolean taxInclusive) {
        this.taxInclusive = taxInclusive;
        this.updatedAt = Instant.now();
    }

    /** Sets the tenant's maximum straight-line delivery distance in km (null = no limit). */
    public void changeMaxDeliveryDistanceKm(BigDecimal maxDeliveryDistanceKm) {
        this.maxDeliveryDistanceKm = maxDeliveryDistanceKm;
        this.updatedAt = Instant.now();
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
    public BigDecimal getServiceFeeRate() { return serviceFeeRate; }
    public BigDecimal getTaxRate() { return taxRate; }
    public long getVersion() { return version; }
    public BigDecimal getCommissionRate() { return commissionRate; }
    public String getCancellationPolicy() { return cancellationPolicy; }
    public boolean isTaxInclusive() { return taxInclusive; }
    public BigDecimal getMaxDeliveryDistanceKm() { return maxDeliveryDistanceKm; }
}
