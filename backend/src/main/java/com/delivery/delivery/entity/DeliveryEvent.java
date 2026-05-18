package com.delivery.delivery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Records delivery lifecycle changes and failed confirmation attempts. */
@Entity
@Table(name = "delivery_events")
public class DeliveryEvent {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;
    @Column(name = "event_type", nullable = false)
    private String eventType;
    private String notes;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DeliveryEvent() {}

    public DeliveryEvent(UUID id, UUID tenantId, UUID deliveryId, String eventType, String notes) {
        this.id = id;
        this.tenantId = tenantId;
        this.deliveryId = deliveryId;
        this.eventType = eventType;
        this.notes = notes;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getDeliveryId() { return deliveryId; }
    public String getEventType() { return eventType; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
}
