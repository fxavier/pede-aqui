package com.delivery.cart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Represents one SKU selection inside a cart. */
@Entity
@Table(name = "cart_items")
public class CartItem {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "sku_id", nullable = false)
    private UUID skuId;
    @Column(name = "product_name_snapshot", nullable = false)
    private String productNameSnapshot;
    @Column(name = "sku_name_snapshot", nullable = false)
    private String skuNameSnapshot;
    @Column(name = "unit_price_snapshot", nullable = false)
    private BigDecimal unitPriceSnapshot;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CartItem() {}

    public CartItem(UUID id, Cart cart, UUID tenantId, UUID skuId, String productNameSnapshot, String skuNameSnapshot, BigDecimal unitPriceSnapshot, int quantity) {
        this.id = id;
        this.cart = cart;
        this.tenantId = tenantId;
        this.skuId = skuId;
        this.productNameSnapshot = productNameSnapshot;
        this.skuNameSnapshot = skuNameSnapshot;
        this.unitPriceSnapshot = unitPriceSnapshot;
        this.quantity = quantity;
        this.createdAt = Instant.now();
    }

    public BigDecimal lineTotal() { return unitPriceSnapshot.multiply(BigDecimal.valueOf(quantity)); }
    public UUID getSkuId() { return skuId; }
    public String getProductNameSnapshot() { return productNameSnapshot; }
    public String getSkuNameSnapshot() { return skuNameSnapshot; }
    public BigDecimal getUnitPriceSnapshot() { return unitPriceSnapshot; }
    public int getQuantity() { return quantity; }
}
