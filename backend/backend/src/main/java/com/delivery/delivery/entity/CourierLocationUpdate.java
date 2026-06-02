package com.delivery.delivery.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "courier_location_updates")
public class CourierLocationUpdate {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "courier_id", nullable = false)
    private UUID courierId;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "accuracy_meters")
    private Double accuracyMeters;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected CourierLocationUpdate() {
    }

    public CourierLocationUpdate(UUID id, UUID tenantId, UUID deliveryId, UUID courierId,
                                Double latitude, Double longitude, Instant recordedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.deliveryId = deliveryId;
        this.courierId = courierId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.recordedAt = recordedAt;
    }

    public void setAccuracyMeters(Double accuracyMeters) {
        this.accuracyMeters = accuracyMeters;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getDeliveryId() {
        return deliveryId;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public Double getAccuracyMeters() {
        return accuracyMeters;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}