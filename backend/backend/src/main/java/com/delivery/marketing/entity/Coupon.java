package com.delivery.marketing.entity;

import com.delivery.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Represents a discount coupon redeemable at checkout. */
@Entity
@Table(name = "coupons")
public class Coupon {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private String code;
    @Column(name = "discount_type", nullable = false)
    private String discountType;
    @Column(name = "discount_value", nullable = false)
    private BigDecimal discountValue;
    @Column(name = "min_order_amount")
    private BigDecimal minOrderAmount;
    @Column(name = "max_uses")
    private Integer maxUses;
    @Column(name = "uses_count", nullable = false)
    private int usesCount;
    @Column(name = "vendor_id")
    private UUID vendorId;
    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;
    @Column(name = "valid_until")
    private Instant validUntil;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Coupon() {}

    public Coupon(UUID id, UUID tenantId, String code, String discountType, BigDecimal discountValue,
                  BigDecimal minOrderAmount, Integer maxUses, UUID vendorId, Instant validFrom, Instant validUntil) {
        this.id = id;
        this.tenantId = tenantId;
        this.code = code.toUpperCase();
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.maxUses = maxUses;
        this.usesCount = 0;
        this.vendorId = vendorId;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void deactivate() {
        if (!active) throw new BusinessException("already_inactive", "Coupon is already inactive", HttpStatus.CONFLICT);
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public BigDecimal getMinOrderAmount() { return minOrderAmount; }
    public Integer getMaxUses() { return maxUses; }
    public int getUsesCount() { return usesCount; }
    public UUID getVendorId() { return vendorId; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getValidUntil() { return validUntil; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}
