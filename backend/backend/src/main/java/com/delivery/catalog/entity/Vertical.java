package com.delivery.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Represents a business vertical (e.g. food, pharmacy) scoped to a tenant. */
@Entity
@Table(name = "verticals")
public class Vertical {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private String slug;
    @Column(nullable = false)
    private String label;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Vertical() {}

    public Vertical(UUID id, UUID tenantId, String label) {
        this.id = id;
        this.tenantId = tenantId;
        this.label = label;
        this.slug = slugify(label);
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String label, boolean active) {
        this.label = label;
        this.slug = slugify(label);
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getSlug() { return slug; }
    public String getLabel() { return label; }
    public boolean isActive() { return active; }

    private static String slugify(String value) {
        return value.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
