package com.delivery.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Represents a purchasable SKU for a product. */
@Entity
@Table(name = "skus")
public class Sku {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    @Column(name = "sku_code", nullable = false)
    private String skuCode;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private BigDecimal price;
    @Column(nullable = false)
    private boolean active;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variant_selection", columnDefinition = "jsonb")
    private Map<String, Object> variantSelection = new HashMap<>(); // Selected variant options for this SKU
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Sku() {}

    public Sku(UUID id, UUID tenantId, UUID productId, String skuCode, String name, BigDecimal price) {
        this.id = id;
        this.tenantId = tenantId;
        this.productId = productId;
        this.skuCode = skuCode;
        this.name = name;
        this.price = price;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProductId() { return productId; }
    public String getSkuCode() { return skuCode; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public boolean isActive() { return active; }
    public Map<String, Object> getVariantSelection() { return variantSelection; }

    public void setVariantSelection(Map<String, Object> variantSelection) {
        this.variantSelection = variantSelection != null ? variantSelection : new HashMap<>();
        this.updatedAt = Instant.now();
    }

    public void setVariantOption(String variantType, Object value) {
        this.variantSelection.put(variantType, value);
        this.updatedAt = Instant.now();
    }

    @SuppressWarnings("unchecked")
    public <T> T getVariantOption(String variantType, Class<T> type) {
        Object value = variantSelection.get(variantType);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }
}
