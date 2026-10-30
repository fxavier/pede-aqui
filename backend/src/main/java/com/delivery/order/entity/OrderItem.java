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
    /** VAT category, rate and VAT contained in line_total before discounts (V064, W6-A); null on older lines. */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "tax_category", length = 20)
    private com.delivery.common.pricing.TaxCategory taxCategory;
    @Column(name = "vat_rate")
    private BigDecimal vatRate;
    @Column(name = "vat_amount")
    private BigDecimal vatAmount;

    protected OrderItem() {}

    public UUID getId() { return id; }
    public UUID getSkuId() { return skuId; }
    public String getProductNameSnapshot() { return productNameSnapshot; }
    public String getSkuNameSnapshot() { return skuNameSnapshot; }
    public BigDecimal getUnitPriceSnapshot() { return unitPriceSnapshot; }
    public int getQuantity() { return quantity; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public UUID getCategoryIdSnapshot() { return categoryIdSnapshot; }
    public com.delivery.common.pricing.TaxCategory getTaxCategory() { return taxCategory; }
    public BigDecimal getVatRate() { return vatRate; }
    public BigDecimal getVatAmount() { return vatAmount; }

    /** Records the line's VAT category, rate and the VAT contained in line_total (extracted, HALF_UP 2 dp). */
    public void recordTax(com.delivery.common.pricing.TaxCategory taxCategory, BigDecimal vatRate, BigDecimal vatAmount) {
        this.taxCategory = taxCategory;
        this.vatRate = vatRate;
        this.vatAmount = vatAmount;
    }

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
