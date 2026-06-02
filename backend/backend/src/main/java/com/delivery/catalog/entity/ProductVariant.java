package com.delivery.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Represents product variants (size, color, flavor, etc.) for vertical-specific customization. */
@Entity
@Table(name = "product_variants")
public class ProductVariant {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    @Column(nullable = false)
    private String name;
    @Column(name = "variant_type", nullable = false)
    private String variantType; // size, color, flavor, model, etc.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variant_options", columnDefinition = "jsonb")
    private Map<String, Object> variantOptions = new HashMap<>(); // Available options and their details
    @Column(nullable = false)
    private boolean required;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "display_order")
    private Integer displayOrder;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductVariant() {}

    public ProductVariant(UUID id, UUID tenantId, UUID productId, String name, String variantType, boolean required) {
        this.id = id;
        this.tenantId = tenantId;
        this.productId = productId;
        this.name = name;
        this.variantType = variantType;
        this.required = required;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.displayOrder = 0;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProductId() { return productId; }
    public String getName() { return name; }
    public String getVariantType() { return variantType; }
    public Map<String, Object> getVariantOptions() { return variantOptions; }
    public boolean isRequired() { return required; }
    public boolean isActive() { return active; }
    public Integer getDisplayOrder() { return displayOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setVariantOptions(Map<String, Object> variantOptions) {
        this.variantOptions = variantOptions != null ? variantOptions : new HashMap<>();
        this.updatedAt = Instant.now();
    }

    public void addVariantOption(String key, Object value) {
        this.variantOptions.put(key, value);
        this.updatedAt = Instant.now();
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
        this.updatedAt = Instant.now();
    }

    public void setActive(boolean active) {
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public void setName(String name) {
        this.name = name;
        this.updatedAt = Instant.now();
    }
}