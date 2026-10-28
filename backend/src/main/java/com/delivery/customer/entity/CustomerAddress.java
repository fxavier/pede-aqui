package com.delivery.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A customer's saved delivery address (tenant-scoped, owned by one customer profile). The GPS pin is
 * written as latitude/longitude; the geography column {@code location} is derived from them by a database
 * trigger during the INC-12 expand phase (V046) and is deliberately not mapped here.
 */
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
    @Column(name = "recipient_name", nullable = false)
    private String recipientName;
    private String phone;
    @Column(nullable = false)
    private String line1;
    private String line2;
    @Column(nullable = false)
    private String city;
    private String state;
    @Column(name = "postal_code")
    private String postalCode;
    private Double latitude;
    private Double longitude;
    @Column(name = "delivery_instructions")
    private String deliveryInstructions;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerAddress() {}

    public CustomerAddress(UUID id, UUID tenantId, UUID customerId) {
        this.id = id;
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** Replaces every editable field (PUT semantics); coordinates are either both set or both null. */
    public void update(String label, String recipientName, String phone, String line1, String line2, String city, String state,
                       String postalCode, Double latitude, Double longitude, String deliveryInstructions) {
        this.label = label;
        this.recipientName = recipientName;
        this.phone = phone;
        this.line1 = line1;
        this.line2 = line2;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.deliveryInstructions = deliveryInstructions;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getCustomerId() { return customerId; }
    public String getLabel() { return label; }
    public String getRecipientName() { return recipientName; }
    public String getPhone() { return phone; }
    public String getLine1() { return line1; }
    public String getLine2() { return line2; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPostalCode() { return postalCode; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getDeliveryInstructions() { return deliveryInstructions; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
