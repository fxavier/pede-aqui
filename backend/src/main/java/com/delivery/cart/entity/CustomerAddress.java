package com.delivery.cart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Stores a customer delivery address used during checkout. */
@Entity
@Table(name = "customer_addresses")
public class CustomerAddress {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(nullable = false)
    private String label;
    @Column(name = "line1", nullable = false)
    private String street;
    @Column(nullable = false)
    private String city;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerAddress() {}

    public CustomerAddress(UUID id, UUID tenantId, UUID customerId, String label, String street, String city) {
        this.id = id;
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.label = label;
        this.street = street;
        this.city = city;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }
}
