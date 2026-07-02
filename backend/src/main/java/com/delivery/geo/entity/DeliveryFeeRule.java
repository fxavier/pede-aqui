package com.delivery.geo.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_fee_rules")
public class DeliveryFeeRule {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Column(name = "min_km", nullable = false, precision = 8, scale = 2)
    private BigDecimal minKm;

    @Column(name = "max_km", nullable = false, precision = 8, scale = 2)
    private BigDecimal maxKm;

    @Column(name = "fee_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal feeAmount;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeliveryFeeRule() {
    }

    public DeliveryFeeRule(UUID id, UUID tenantId, UUID vendorId, 
                          BigDecimal minKm, BigDecimal maxKm, BigDecimal feeAmount) {
        this.id = id;
        this.tenantId = tenantId;
        this.vendorId = vendorId;
        this.minKm = minKm;
        this.maxKm = maxKm;
        this.feeAmount = feeAmount;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void updateFeeAmount(BigDecimal feeAmount) {
        this.feeAmount = feeAmount;
        this.updatedAt = Instant.now();
    }

    public void updateDistanceRange(BigDecimal minKm, BigDecimal maxKm) {
        this.minKm = minKm;
        this.maxKm = maxKm;
        this.updatedAt = Instant.now();
    }

    public void setActive(Boolean active) {
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public boolean isApplicableForDistance(BigDecimal distance) {
        return active && 
               distance.compareTo(minKm) >= 0 && 
               distance.compareTo(maxKm) <= 0;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getVendorId() {
        return vendorId;
    }

    public BigDecimal getMinKm() {
        return minKm;
    }

    public BigDecimal getMaxKm() {
        return maxKm;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public Boolean getActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}