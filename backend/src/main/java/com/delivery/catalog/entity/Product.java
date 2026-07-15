package com.delivery.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
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
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes", columnDefinition = "jsonb")
    private Map<String, Object> attributes = new HashMap<>();
    @Column(name = "primary_image_key")
    private String primaryImageKey; // S3 storage key for primary product image
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "image_gallery", columnDefinition = "jsonb")
    private java.util.List<String> imageGallery = new java.util.ArrayList<>(); // Additional product images
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
        this.status = "PENDING"; // Start as PENDING for admin review
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void markPrescriptionRequired() { this.requiresPrescriptionMetadata = true; this.manualValidationRequired = true; }
    public void markProhibitedFuel() { this.prohibitedFuel = true; }
    public void approve() { this.status = "ACTIVE"; this.updatedAt = Instant.now(); }
    public void reject() { this.status = "REJECTED"; this.updatedAt = Instant.now(); }
    public boolean isPending() { return "PENDING".equals(this.status); }
    public boolean isActive() { return "ACTIVE".equals(this.status); }
    public boolean isRejected() { return "REJECTED".equals(this.status); }

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
    public Map<String, Object> getAttributes() { return attributes; }
    public String getPrimaryImageKey() { return primaryImageKey; }
    public java.util.List<String> getImageGallery() { return imageGallery; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String name) {
        this.name = name;
        this.updatedAt = Instant.now();
    }

    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
        this.updatedAt = Instant.now();
    }

    /** Toggles the prescription flag; manual validation mirrors it, matching markPrescriptionRequired(). */
    public void setRequiresPrescription(boolean requiresPrescription) {
        this.requiresPrescriptionMetadata = requiresPrescription;
        this.manualValidationRequired = requiresPrescription;
        this.updatedAt = Instant.now();
    }

    public void setAttribute(String key, Object value) {
        this.attributes.put(key, value);
        this.updatedAt = Instant.now();
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes != null ? attributes : new HashMap<>();
        this.updatedAt = Instant.now();
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, Class<T> type) {
        Object value = attributes.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }

    public void setPrimaryImageKey(String primaryImageKey) {
        this.primaryImageKey = primaryImageKey;
        this.updatedAt = Instant.now();
    }

    public void setImageGallery(java.util.List<String> imageGallery) {
        this.imageGallery = imageGallery != null ? imageGallery : new java.util.ArrayList<>();
        this.updatedAt = Instant.now();
    }

    public void addImage(String imageKey) {
        if (this.primaryImageKey == null) {
            this.primaryImageKey = imageKey;
        } else {
            this.imageGallery.add(imageKey);
        }
        this.updatedAt = Instant.now();
    }

    public void removeImage(String imageKey) {
        if (imageKey.equals(this.primaryImageKey)) {
            if (!this.imageGallery.isEmpty()) {
                this.primaryImageKey = this.imageGallery.remove(0);
            } else {
                this.primaryImageKey = null;
            }
        } else {
            this.imageGallery.remove(imageKey);
        }
        this.updatedAt = Instant.now();
    }
}
