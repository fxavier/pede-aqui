package com.delivery.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Represents a vendor product with MVP safety and pharmacy flags. */
@Entity
@Table(name = "products")
public class Product {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;
    @Column(name = "category_id", nullable = false)
    private UUID categoryId;
    @Column(nullable = false)
    private String name;
    private String description;
    @Column(nullable = false)
    private String status;
    @Column(name = "requires_prescription_metadata", nullable = false)
    private boolean requiresPrescriptionMetadata;
    @Column(name = "manual_validation_required", nullable = false)
    private boolean manualValidationRequired;
    @Column(name = "prohibited_fuel", nullable = false)
    private boolean prohibitedFuel;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Product() {}

    public Product(UUID id, UUID tenantId, UUID vendorId, UUID categoryId, String name, String description) {
        this.id = id;
        this.tenantId = tenantId;
        this.vendorId = vendorId;
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void markPrescriptionRequired() { this.requiresPrescriptionMetadata = true; this.manualValidationRequired = true; }
    public void markProhibitedFuel() { this.prohibitedFuel = true; }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getVendorId() { return vendorId; }
    public UUID getCategoryId() { return categoryId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public boolean isRequiresPrescriptionMetadata() { return requiresPrescriptionMetadata; }
    public boolean isManualValidationRequired() { return manualValidationRequired; }
    public boolean isProhibitedFuel() { return prohibitedFuel; }
}
