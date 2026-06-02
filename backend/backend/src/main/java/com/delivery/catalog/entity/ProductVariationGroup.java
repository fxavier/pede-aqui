package com.delivery.catalog.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "product_variation_groups")
public class ProductVariationGroup {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private Boolean required = false;

    @Column(name = "min_selections", nullable = false)
    private Integer minSelections = 0;

    @Column(name = "max_selections", nullable = false)
    private Integer maxSelections = 1;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "groupId", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVariationOption> options = new ArrayList<>();

    protected ProductVariationGroup() {
    }

    public ProductVariationGroup(UUID id, UUID tenantId, UUID productId, String name) {
        this.id = id;
        this.tenantId = tenantId;
        this.productId = productId;
        this.name = name;
        this.required = false;
        this.minSelections = 0;
        this.maxSelections = 1;
        this.displayOrder = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void setRequired(Boolean required) {
        this.required = required;
        this.updatedAt = Instant.now();
    }

    public void setSelectionLimits(Integer minSelections, Integer maxSelections) {
        this.minSelections = minSelections;
        this.maxSelections = maxSelections;
        this.updatedAt = Instant.now();
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public Boolean getRequired() {
        return required;
    }

    public Integer getMinSelections() {
        return minSelections;
    }

    public Integer getMaxSelections() {
        return maxSelections;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<ProductVariationOption> getOptions() {
        return options;
    }
}