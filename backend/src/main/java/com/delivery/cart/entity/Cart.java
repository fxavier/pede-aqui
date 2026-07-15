package com.delivery.cart.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Represents a customer's active single-vendor cart. */
@Entity
@Table(name = "carts")
public class Cart {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;
    @Column(nullable = false)
    private String status;
    @Column(nullable = false)
    private BigDecimal subtotal = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal fees = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal taxes = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal discounts = BigDecimal.ZERO;
    @Column(name = "applied_promotion_id")
    private UUID appliedPromotionId;
    @Column(name = "coupon_code")
    private String couponCode;
    @Column(name = "discount_total", nullable = false)
    private BigDecimal discountTotal = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal total = BigDecimal.ZERO;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    protected Cart() {}

    public Cart(UUID id, UUID tenantId, UUID customerId, UUID vendorId) {
        this.id = id;
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.vendorId = vendorId;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void addItem(CartItem item) { items.add(item); recalculateSubtotal(); updatedAt = Instant.now(); }
    public void updateTotals(BigDecimal fees, BigDecimal taxes, BigDecimal discounts, BigDecimal total) { this.fees = fees; this.taxes = taxes; this.discounts = discounts; this.total = total; }
    public void markCheckedOut() { this.status = "CHECKED_OUT"; this.updatedAt = Instant.now(); }
    /** Attaches a validated promotion/coupon discount pending re-validation at checkout. */
    public void applyPromotion(UUID appliedPromotionId, String couponCode, BigDecimal discountTotal) { this.appliedPromotionId = appliedPromotionId; this.couponCode = couponCode; this.discountTotal = discountTotal; this.updatedAt = Instant.now(); }
    /** Clears any pending promotion/coupon discount. */
    public void clearPromotion() { this.appliedPromotionId = null; this.couponCode = null; this.discountTotal = BigDecimal.ZERO; this.updatedAt = Instant.now(); }
    private void recalculateSubtotal() { subtotal = items.stream().map(CartItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add); }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getVendorId() { return vendorId; }
    public String getStatus() { return status; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getFees() { return fees; }
    public BigDecimal getTaxes() { return taxes; }
    public BigDecimal getDiscounts() { return discounts; }
    public UUID getAppliedPromotionId() { return appliedPromotionId; }
    public String getCouponCode() { return couponCode; }
    public BigDecimal getDiscountTotal() { return discountTotal; }
    public BigDecimal getTotal() { return total; }
    public List<CartItem> getItems() { return items; }
}
