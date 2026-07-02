package com.delivery.notification.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_notification_preferences")
public class UserNotificationPreference {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_profile_id", nullable = false)
    private UUID userProfileId;

    @Column(name = "push_enabled", nullable = false)
    private Boolean pushEnabled = true;

    @Column(name = "sms_enabled", nullable = false)
    private Boolean smsEnabled = false;

    @Column(name = "email_enabled", nullable = false)
    private Boolean emailEnabled = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserNotificationPreference() {
    }

    public UserNotificationPreference(UUID id, UUID tenantId, UUID userProfileId) {
        this.id = id;
        this.tenantId = tenantId;
        this.userProfileId = userProfileId;
        this.pushEnabled = true;
        this.smsEnabled = false;
        this.emailEnabled = false;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void updatePreferences(Boolean pushEnabled, Boolean smsEnabled, Boolean emailEnabled) {
        this.pushEnabled = pushEnabled;
        this.smsEnabled = smsEnabled;
        this.emailEnabled = emailEnabled;
        this.updatedAt = Instant.now();
    }

    public void setPushEnabled(Boolean pushEnabled) {
        this.pushEnabled = pushEnabled;
        this.updatedAt = Instant.now();
    }

    public void setSmsEnabled(Boolean smsEnabled) {
        this.smsEnabled = smsEnabled;
        this.updatedAt = Instant.now();
    }

    public void setEmailEnabled(Boolean emailEnabled) {
        this.emailEnabled = emailEnabled;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUserProfileId() {
        return userProfileId;
    }

    public Boolean getPushEnabled() {
        return pushEnabled;
    }

    public Boolean getSmsEnabled() {
        return smsEnabled;
    }

    public Boolean getEmailEnabled() {
        return emailEnabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}