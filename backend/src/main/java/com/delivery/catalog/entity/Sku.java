package com.delivery.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
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
}
