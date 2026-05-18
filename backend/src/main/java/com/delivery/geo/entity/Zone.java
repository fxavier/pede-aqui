package com.delivery.geo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Represents an operations zone used for courier assignment and policy scoping. */
@Entity
@Table(name = "zones")
public class Zone {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String status;
    @Column(name = "geometry_wkt")
    private String geometryWkt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Zone() {}

    public Zone(UUID id, UUID tenantId, String name, String geometryWkt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.status = "ACTIVE";
        this.geometryWkt = geometryWkt;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public String getGeometryWkt() { return geometryWkt; }
}
