package com.delivery.vendor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Explicit user ↔ vendor ↔ tenant association (ADR-0001 §3, table merchant_users, V049). Source of truth for
 * the merchant scope guards: a VENDOR_ADMIN/VENDOR_STAFF principal may only act on vendors it is associated with.
 */
@Entity
@Table(name = "merchant_users")
public class MerchantUser {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;
    @Column(name = "app_user_profile_id", nullable = false)
    private UUID appUserProfileId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MerchantRole role;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MerchantUser() {}

    public MerchantUser(UUID id, UUID tenantId, UUID vendorId, UUID appUserProfileId, MerchantRole role) {
        this.id = id;
        this.tenantId = tenantId;
        this.vendorId = vendorId;
        this.appUserProfileId = appUserProfileId;
        this.role = role;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getVendorId() { return vendorId; }
    public UUID getAppUserProfileId() { return appUserProfileId; }
    public MerchantRole getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
