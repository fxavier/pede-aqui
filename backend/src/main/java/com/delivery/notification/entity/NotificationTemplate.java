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

/**
 * A notification text for one (key, channel, locale) (V067, LAC-48). {@code tenantId == null} is the platform default;
 * a tenant row overrides it. {@code version} counts content changes (copied into each delivery for traceability).
 */
@Entity
@Table(name = "notification_templates")
public class NotificationTemplate {
    @Id
    private UUID id;
    @Column(name = "tenant_id")
    private UUID tenantId;
    @Column(name = "template_key", nullable = false)
    private String templateKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;
    @Column(nullable = false)
    private String locale;
    private String title;
    @Column(nullable = false)
    private String body;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "allow_sensitive_placeholders", nullable = false)
    private boolean allowSensitivePlaceholders;
    @Column(nullable = false)
    private int version;
    @Column(name = "updated_by")
    private String updatedBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationTemplate() {
    }

    /** A new tenant override, starting after the default's version so deliveries show which text they used. */
    public static NotificationTemplate overrideOf(UUID tenantId, String templateKey, NotificationChannel channel, String locale,
                                                  int startVersion) {
        NotificationTemplate template = new NotificationTemplate();
        template.id = UUID.randomUUID();
        template.tenantId = tenantId;
        template.templateKey = templateKey;
        template.channel = channel;
        template.locale = locale;
        template.version = startVersion;
        template.createdAt = Instant.now();
        template.updatedAt = template.createdAt;
        return template;
    }

    /** Replaces the content and bumps the version. */
    public void update(String title, String body, boolean active, String updatedBy, boolean bumpVersion) {
        this.title = title;
        this.body = body;
        this.active = active;
        this.updatedBy = updatedBy;
        if (bumpVersion) {
            this.version++;
        }
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getTemplateKey() { return templateKey; }
    public NotificationChannel getChannel() { return channel; }
    public String getLocale() { return locale; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public boolean isActive() { return active; }
    public boolean isAllowSensitivePlaceholders() { return allowSensitivePlaceholders; }
    public int getVersion() { return version; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
