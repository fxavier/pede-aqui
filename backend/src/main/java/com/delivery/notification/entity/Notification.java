package com.delivery.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Represents a persisted user notification for marketplace events. */
@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;
    @Column(name = "recipient_role", nullable = false)
    private String recipientRole;
    @Column(nullable = false)
    private String type;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String message;
    @Column(name = "business_reference")
    private String businessReference;
    @Column(name = "read_at")
    private Instant readAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() {}

    public Notification(UUID id, UUID tenantId, UUID recipientUserId, String recipientRole, String type, String title, String message, String businessReference) {
        this.id = id;
        this.tenantId = tenantId;
        this.recipientUserId = recipientUserId;
        this.recipientRole = recipientRole;
        this.type = type;
        this.title = title;
        this.message = message;
        this.businessReference = businessReference;
        this.createdAt = Instant.now();
    }

    public void markRead() { this.readAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getRecipientUserId() { return recipientUserId; }
    public String getRecipientRole() { return recipientRole; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getBusinessReference() { return businessReference; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
}
