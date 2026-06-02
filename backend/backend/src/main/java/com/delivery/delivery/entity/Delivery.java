package com.delivery.delivery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Tracks delivery status and customer confirmation code validation. */
@Entity
@Table(name = "deliveries")
public class Delivery {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "order_id", nullable = false)
    private UUID orderId;
    @Column(name = "courier_id")
    private UUID courierId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;
    @Column(name = "confirmation_code_hash", nullable = false)
    private String confirmationCodeHash;
    @Column(name = "confirmation_attempts", nullable = false)
    private int confirmationAttempts;
    @Column(name = "proof_photo_storage_key")
    private String proofPhotoStorageKey;
    @Column(name = "cash_collected_amount")
    private BigDecimal cashCollectedAmount;
    @Column(name = "proximity_notified", nullable = false)
    private Boolean proximityNotified = false;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Delivery() {}

    public Delivery(UUID id, UUID tenantId, UUID orderId, String confirmationCodeHash) {
        this.id = id;
        this.tenantId = tenantId;
        this.orderId = orderId;
        this.status = DeliveryStatus.DISPATCH_PENDING;
        this.confirmationCodeHash = confirmationCodeHash;
        this.proximityNotified = false;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void markDelivered() { status = DeliveryStatus.DELIVERED; updatedAt = Instant.now(); }
    public void recordFailedAttempt() { confirmationAttempts++; updatedAt = Instant.now(); }
    public void assignCourier(UUID courierId) { this.courierId = courierId; this.status = DeliveryStatus.ASSIGNED; this.updatedAt = Instant.now(); }
    public void updateStatus(DeliveryStatus status) { this.status = status; this.updatedAt = Instant.now(); }
    public void recordCashCollected(BigDecimal amount) { this.cashCollectedAmount = amount; this.updatedAt = Instant.now(); }
    public void setProofPhotoStorageKey(String proofPhotoStorageKey) { this.proofPhotoStorageKey = proofPhotoStorageKey; this.updatedAt = Instant.now(); }
    public void markProximityNotified() { this.proximityNotified = true; this.updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getOrderId() { return orderId; }
    public UUID getCourierId() { return courierId; }
    public DeliveryStatus getStatus() { return status; }
    public String getConfirmationCodeHash() { return confirmationCodeHash; }
    public int getConfirmationAttempts() { return confirmationAttempts; }
    public Boolean getProximityNotified() { return proximityNotified; }
}
