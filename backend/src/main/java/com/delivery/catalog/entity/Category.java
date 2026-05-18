package com.delivery.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Represents a tenant-scoped marketplace catalog category. */
@Entity
@Table(name = "categories")
public class Category {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String slug;
    @Column(nullable = false)
    private String vertical;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Category() {}

    public Category(UUID id, UUID tenantId, String name, String vertical) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.slug = slugify(name);
        this.vertical = vertical;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getVertical() { return vertical; }
    public boolean isActive() { return active; }

    private static String slugify(String value) {
        return value.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
