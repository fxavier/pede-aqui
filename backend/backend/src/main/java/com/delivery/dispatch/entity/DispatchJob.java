package com.delivery.dispatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Represents a courier assignment for a delivery job. */
@Entity
@Table(name = "dispatch_jobs")
public class DispatchJob {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "order_id", nullable = false)
    private UUID orderId;
    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;
    @Column(name = "courier_id")
    private UUID courierId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DispatchJobStatus status;
    @Column(name = "rejection_reason")
    private String rejectionReason;
    @Column(name = "assigned_at")
    private Instant assignedAt;
    @Column(name = "accepted_at")
    private Instant acceptedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected DispatchJob() {}

    public DispatchJob(UUID id, UUID tenantId, UUID orderId, UUID deliveryId, UUID courierId) {
        this.id = id;
        this.tenantId = tenantId;
        this.orderId = orderId;
        this.deliveryId = deliveryId;
        this.courierId = courierId;
        this.status = DispatchJobStatus.ASSIGNED;
        this.assignedAt = Instant.now();
        this.createdAt = this.assignedAt;
        this.updatedAt = this.assignedAt;
    }

    public void accept() { this.status = DispatchJobStatus.ACCEPTED; this.acceptedAt = Instant.now(); this.updatedAt = this.acceptedAt; }
    public void reject(String reason) { this.status = DispatchJobStatus.REASSIGNABLE; this.rejectionReason = reason; this.updatedAt = Instant.now(); }
    public void reassign(UUID courierId) { this.courierId = courierId; this.status = DispatchJobStatus.ASSIGNED; this.assignedAt = Instant.now(); this.updatedAt = this.assignedAt; }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getOrderId() { return orderId; }
    public UUID getDeliveryId() { return deliveryId; }
    public UUID getCourierId() { return courierId; }
    public DispatchJobStatus getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
}
