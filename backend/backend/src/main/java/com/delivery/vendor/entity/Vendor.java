package com.delivery.vendor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Represents a tenant vendor profile used in discovery and fulfillment. */
@Entity
@Table(name = "vendors")
public class Vendor {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private String name;
    @Column(name = "category_id")
    private UUID categoryId;
    @Column(nullable = false)
    private String status;
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    private VendorVerificationStatus verificationStatus;
    @Column(nullable = false)
    private double rating;
    @Column(name = "estimated_delivery_minutes", nullable = false)
    private int estimatedDeliveryMinutes;
    @Column(nullable = false)
    private boolean available;
    @Column
    private Double latitude;
    @Column
    private Double longitude;
    @Column(name = "owner_name")
    private String ownerName;
    @Column
    private String nif;
    @Column
    private String phone;
    @Column
    private String address;
    @Column
    private String description;
    @Column(name = "logo_storage_key")
    private String logoStorageKey;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Vendor() {}

    public Vendor(UUID id, UUID tenantId, String name, UUID categoryId, Double latitude, Double longitude, 
                  String ownerName, String nif, String phone, String address, String description, String logoStorageKey) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.categoryId = categoryId;
        this.status = "ACTIVE";
        this.verificationStatus = VendorVerificationStatus.PENDING;
        this.rating = 0.0d;
        this.estimatedDeliveryMinutes = 45;
        this.available = false;
        this.latitude = latitude;
        this.longitude = longitude;
        this.ownerName = ownerName;
        this.nif = nif;
        this.phone = phone;
        this.address = address;
        this.description = description;
        this.logoStorageKey = logoStorageKey;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void setAvailability(boolean available) { this.available = available; this.updatedAt = Instant.now(); }
    public void setEstimatedDeliveryMinutes(int minutes) { this.estimatedDeliveryMinutes = minutes; this.updatedAt = Instant.now(); }
    public void updateProfile(String name, UUID categoryId, Double latitude, Double longitude, 
                              String ownerName, String nif, String phone, String address, String description, String logoStorageKey) {
        this.name = name;
        this.categoryId = categoryId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.ownerName = ownerName;
        this.nif = nif;
        this.phone = phone;
        this.address = address;
        this.description = description;
        this.logoStorageKey = logoStorageKey;
        this.updatedAt = Instant.now();
    }
    public void approveVerification() { this.verificationStatus = VendorVerificationStatus.APPROVED; this.updatedAt = Instant.now(); }
    public void rejectVerification() { this.verificationStatus = VendorVerificationStatus.REJECTED; this.updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public UUID getCategoryId() { return categoryId; }
    public String getStatus() { return status; }
    public VendorVerificationStatus getVerificationStatus() { return verificationStatus; }
    public double getRating() { return rating; }
    public int getEstimatedDeliveryMinutes() { return estimatedDeliveryMinutes; }
    public boolean isAvailable() { return available; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getOwnerName() { return ownerName; }
    public String getNif() { return nif; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public String getDescription() { return description; }
    public String getLogoStorageKey() { return logoStorageKey; }
}
