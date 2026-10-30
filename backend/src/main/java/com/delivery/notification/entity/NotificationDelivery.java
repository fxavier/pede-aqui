package com.delivery.notification.entity;

import com.delivery.notification.api.NotificationChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * One per-channel notification delivery (V067, LAC-48): the outbox row and its delivery log. Read-only in JPA —
 * inserts ({@code ON CONFLICT DO NOTHING} on the idempotency key), claims and result updates go through
 * {@code NotificationDeliveryStore} (JDBC) so the job's claim/lease protocol stays explicit.
 */
@Entity
@Immutable
@Table(name = "notification_deliveries")
public class NotificationDelivery {

    /** Delivery status. */
    public enum Status { QUEUED, SENT, FAILED, SKIPPED }

    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "recipient_profile_id", nullable = false)
    private UUID recipientProfileId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;
    @Column(name = "template_key", nullable = false)
    private String templateKey;
    @Column(name = "template_version", nullable = false)
    private int templateVersion;
    @Column(nullable = false)
    private String locale;
    private String title;
    @Column(nullable = false)
    private String body;
    @Column(name = "business_reference")
    private String businessReference;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "last_error")
    private String lastError;
    @Column(name = "provider_message_id")
    private String providerMessageId;
    @Column(name = "recipient_masked")
    private String recipientMasked;
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "sent_at")
    private Instant sentAt;

    protected NotificationDelivery() {
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getRecipientProfileId() { return recipientProfileId; }
    public NotificationChannel getChannel() { return channel; }
    public String getTemplateKey() { return templateKey; }
    public int getTemplateVersion() { return templateVersion; }
    public String getLocale() { return locale; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getBusinessReference() { return businessReference; }
    public Status getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
    public String getProviderMessageId() { return providerMessageId; }
    public String getRecipientMasked() { return recipientMasked; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getSentAt() { return sentAt; }
}
