package com.delivery.vendor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/** Stores one vendor opening-hour rule per day of week. */
@Entity
@Table(name = "vendor_opening_hours")
public class VendorOpeningHour {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;
    @Column(name = "opens_at")
    private LocalTime opensAt;
    @Column(name = "closes_at")
    private LocalTime closesAt;
    @Column(nullable = false)
    private boolean closed;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VendorOpeningHour() {}

    public VendorOpeningHour(UUID id, UUID tenantId, UUID vendorId, int dayOfWeek, LocalTime opensAt, LocalTime closesAt, boolean closed) {
        this.id = id;
        this.tenantId = tenantId;
        this.vendorId = vendorId;
        this.dayOfWeek = dayOfWeek;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
        this.closed = closed;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getVendorId() { return vendorId; }
    public int getDayOfWeek() { return dayOfWeek; }
    public LocalTime getOpensAt() { return opensAt; }
    public LocalTime getClosesAt() { return closesAt; }
    public boolean isClosed() { return closed; }
}
