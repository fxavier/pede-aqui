package com.delivery.marketing.entity;

import com.delivery.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Represents a time-bounded promotional discount applied automatically to qualifying orders. */
@Entity
@Table(name = "promotions")
public class Promotion {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private String name;
    @Column
    private String description;
    @Column(name = "discount_type", nullable = false)
    private String discountType;
    @Column(name = "discount_value", nullable = false)
    private BigDecimal discountValue;
    @Column(name = "vendor_id")
    private UUID vendorId;
    @Column(name = "applies_to", nullable = false)
    private String appliesTo;
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;
    @Column(name = "ends_at")
    private Instant endsAt;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Promotion() {}

    public Promotion(UUID id, UUID tenantId, String name, String description, String discountType,
                     BigDecimal discountValue, UUID vendorId, String appliesTo, Instant startsAt, Instant endsAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.description = description;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.vendorId = vendorId;
        this.appliesTo = appliesTo;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void deactivate() {
        if (!active) throw new BusinessException("already_inactive", "Promotion is already inactive", HttpStatus.CONFLICT);
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public UUID getVendorId() { return vendorId; }
    public String getAppliesTo() { return appliesTo; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}
