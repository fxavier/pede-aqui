package com.delivery.order.entity;

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

/** Stores immutable item snapshots for an order. */
@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
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
    @Column(name = "line_total", nullable = false)
    private BigDecimal lineTotal;
    @Column(name = "category_id_snapshot")
    private UUID categoryIdSnapshot;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderItem() {}

    public UUID getId() { return id; }
    public String getProductNameSnapshot() { return productNameSnapshot; }
    public String getSkuNameSnapshot() { return skuNameSnapshot; }
    public BigDecimal getUnitPriceSnapshot() { return unitPriceSnapshot; }
    public int getQuantity() { return quantity; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public UUID getCategoryIdSnapshot() { return categoryIdSnapshot; }

    public OrderItem(UUID id, Order order, UUID tenantId, UUID skuId, String productNameSnapshot, String skuNameSnapshot, BigDecimal unitPriceSnapshot, int quantity, UUID categoryIdSnapshot) {
        this(id, order, tenantId, skuId, productNameSnapshot, skuNameSnapshot, unitPriceSnapshot, quantity);
        this.categoryIdSnapshot = categoryIdSnapshot;
    }

    public OrderItem(UUID id, Order order, UUID tenantId, UUID skuId, String productNameSnapshot, String skuNameSnapshot, BigDecimal unitPriceSnapshot, int quantity) {
        this.id = id;
        this.order = order;
        this.tenantId = tenantId;
        this.skuId = skuId;
        this.productNameSnapshot = productNameSnapshot;
        this.skuNameSnapshot = skuNameSnapshot;
        this.unitPriceSnapshot = unitPriceSnapshot;
        this.quantity = quantity;
        this.lineTotal = unitPriceSnapshot.multiply(BigDecimal.valueOf(quantity));
        this.createdAt = Instant.now();
    }
}
