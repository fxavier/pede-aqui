package com.delivery.inventory.entity;

import com.delivery.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Tracks available and reserved stock for one SKU. */
@Entity
@Table(name = "inventory_items")
public class InventoryItem {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;
    @Column(name = "sku_id", nullable = false)
    private UUID skuId;
    @Column(name = "quantity_available", nullable = false)
    private int quantityAvailable;
    @Column(name = "quantity_reserved", nullable = false)
    private int quantityReserved;
    @Column(nullable = false)
    private String status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected InventoryItem() {}

    public InventoryItem(UUID id, UUID tenantId, UUID vendorId, UUID skuId, int quantityAvailable) {
        if (quantityAvailable < 0) throw new BusinessException("negative_stock", "Stock cannot be negative", HttpStatus.BAD_REQUEST);
        this.id = id;
        this.tenantId = tenantId;
        this.vendorId = vendorId;
        this.skuId = skuId;
        this.quantityAvailable = quantityAvailable;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void reserve(int quantity) {
        if (quantity <= 0) throw new BusinessException("invalid_quantity", "Quantity must be positive", HttpStatus.BAD_REQUEST);
        if (quantityAvailable < quantity) throw new BusinessException("insufficient_stock", "Not enough stock is available", HttpStatus.CONFLICT);
        quantityAvailable -= quantity;
        quantityReserved += quantity;
        updatedAt = Instant.now();
    }

    public void updateAvailable(int quantityAvailable) {
        if (quantityAvailable < 0) throw new BusinessException("negative_stock", "Stock cannot be negative", HttpStatus.BAD_REQUEST);
        this.quantityAvailable = quantityAvailable;
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getVendorId() { return vendorId; }
    public UUID getSkuId() { return skuId; }
    public int getQuantityAvailable() { return quantityAvailable; }
    public int getQuantityReserved() { return quantityReserved; }
}
