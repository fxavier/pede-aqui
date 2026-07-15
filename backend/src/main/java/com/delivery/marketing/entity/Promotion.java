package com.delivery.marketing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Spec-002 promotion (table {@code promotion}): coupon (has code) or automatic (no code) discount,
 * vendor-scoped or tenant-wide, with a validity window and usage limits.
 * Distinct from the legacy V013 {@code promotions} table, which is no longer mapped.
 */
@Entity
@Table(name = "promotion")
public class Promotion {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "vendor_id")
    private UUID vendorId;
    @Column(nullable = false, length = 140)
    private String name;
    @Column(length = 40)
    private String code;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromotionType type;
    @Column(nullable = false)
    private BigDecimal value;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromotionScope scope;
    @Column(name = "target_category_id")
    private UUID targetCategoryId;
    @Column(name = "target_product_id")
    private UUID targetProductId;
    @Column(name = "min_order_total")
    private BigDecimal minOrderTotal;
    @Column(name = "max_discount_amount")
    private BigDecimal maxDiscountAmount;
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;
    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;
    @Column(name = "usage_limit")
    private Integer usageLimit;
    @Column(name = "per_customer_limit")
    private Integer perCustomerLimit;
    @Column(name = "used_count", nullable = false)
    private int usedCount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromotionStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Promotion() {}

    public Promotion(UUID id, UUID tenantId, UUID vendorId, String name, String code, PromotionType type,
                     BigDecimal value, PromotionScope scope, UUID targetCategoryId, UUID targetProductId,
                     BigDecimal minOrderTotal, BigDecimal maxDiscountAmount, Instant startsAt, Instant endsAt,
                     Integer usageLimit, Integer perCustomerLimit) {
        this.id = id;
        this.tenantId = tenantId;
        this.status = PromotionStatus.DRAFT;
        this.usedCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        applyDetails(vendorId, name, code, type, value, scope, targetCategoryId, targetProductId,
                minOrderTotal, maxDiscountAmount, startsAt, endsAt, usageLimit, perCustomerLimit);
    }

    /** Replaces the editable promotion attributes (validation happens in the service). */
    public void applyDetails(UUID vendorId, String name, String code, PromotionType type, BigDecimal value,
                             PromotionScope scope, UUID targetCategoryId, UUID targetProductId,
                             BigDecimal minOrderTotal, BigDecimal maxDiscountAmount, Instant startsAt,
                             Instant endsAt, Integer usageLimit, Integer perCustomerLimit) {
        this.vendorId = vendorId;
        this.name = name;
        this.code = code;
        this.type = type;
        this.value = value;
        this.scope = scope;
        this.targetCategoryId = targetCategoryId;
        this.targetProductId = targetProductId;
        this.minOrderTotal = minOrderTotal;
        this.maxDiscountAmount = maxDiscountAmount;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.usageLimit = usageLimit;
        this.perCustomerLimit = perCustomerLimit;
        this.updatedAt = Instant.now();
    }

    public void activate() { this.status = PromotionStatus.ACTIVE; this.updatedAt = Instant.now(); }
    public void pause() { this.status = PromotionStatus.PAUSED; this.updatedAt = Instant.now(); }
    public void markExpired() { this.status = PromotionStatus.EXPIRED; this.updatedAt = Instant.now(); }

    /** True when the validity window has closed. */
    public boolean isExpired(Instant now) { return now.isAfter(endsAt); }

    /** True when the validity window contains the instant. */
    public boolean isWithinWindow(Instant now) { return !now.isBefore(startsAt) && !now.isAfter(endsAt); }

    /** Effective status: EXPIRED once past ends_at regardless of the stored status. */
    public PromotionStatus effectiveStatus(Instant now) {
        return isExpired(now) ? PromotionStatus.EXPIRED : status;
    }

    /** True while the global usage cap (null = unlimited) has headroom; races are settled by the conditional UPDATE at checkout. */
    public boolean hasGlobalUsageHeadroom() { return usageLimit == null || usedCount < usageLimit; }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getVendorId() { return vendorId; }
    public String getName() { return name; }
    public String getCode() { return code; }
    public PromotionType getType() { return type; }
    public BigDecimal getValue() { return value; }
    public PromotionScope getScope() { return scope; }
    public UUID getTargetCategoryId() { return targetCategoryId; }
    public UUID getTargetProductId() { return targetProductId; }
    public BigDecimal getMinOrderTotal() { return minOrderTotal; }
    public BigDecimal getMaxDiscountAmount() { return maxDiscountAmount; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public Integer getUsageLimit() { return usageLimit; }
    public Integer getPerCustomerLimit() { return perCustomerLimit; }
    public int getUsedCount() { return usedCount; }
    public PromotionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
