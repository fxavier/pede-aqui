package com.delivery.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** Customer's favorite vendor relationship for quick access in search results. */
@Entity
@Table(name = "customer_favorite_vendors", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "customer_id", "vendor_id"}))
public class CustomerFavoriteVendor {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    
    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public CustomerFavoriteVendor() {}

    public CustomerFavoriteVendor(UUID tenantId, UUID customerId, UUID vendorId) {
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.vendorId = vendorId;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }

    public UUID getVendorId() { return vendorId; }
    public void setVendorId(UUID vendorId) { this.vendorId = vendorId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}