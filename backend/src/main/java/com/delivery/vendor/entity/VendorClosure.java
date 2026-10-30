package com.delivery.vendor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Exceptional store closure (LAC-27, table vendor_closures, V055): the vendor is closed during [startsAt, endsAt). */
@Entity
@Table(name = "vendor_closures")
public class VendorClosure {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;
    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;
    @Column(nullable = false)
    private String reason;
    @Column(name = "created_by")
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected VendorClosure() {}

    public VendorClosure(UUID id, UUID tenantId, UUID vendorId, Instant startsAt, Instant endsAt, String reason, String createdBy) {
        this.id = id;
        this.tenantId = tenantId;
        this.vendorId = vendorId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.reason = reason;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    /** True when the instant falls inside the half-open closure interval. */
    public boolean covers(Instant at) {
        return !at.isBefore(startsAt) && at.isBefore(endsAt);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getVendorId() { return vendorId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public String getReason() { return reason; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
