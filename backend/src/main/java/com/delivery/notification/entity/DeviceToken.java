package com.delivery.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A push token of one user's device (V067, LAC-48). Personal data: the raw token is kept only because the provider
 * needs it, is never logged or returned, and the row is deleted on unregister or when the provider rejects it.
 */
@Entity
@Table(name = "device_tokens")
public class DeviceToken {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "user_profile_id", nullable = false)
    private UUID userProfileId;
    @Column(nullable = false, length = 10)
    private String platform;
    @Column(nullable = false, length = 4096)
    private String token;
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected DeviceToken() {
    }

    public DeviceToken(UUID id, UUID tenantId, UUID userProfileId, String platform, String token, String tokenHash) {
        this.id = id;
        this.tenantId = tenantId;
        this.userProfileId = userProfileId;
        this.platform = platform;
        this.token = token;
        this.tokenHash = tokenHash;
        this.createdAt = Instant.now();
        this.lastSeenAt = this.createdAt;
    }

    /** Re-registration: the device may now belong to another profile/tenant or report another platform. */
    public void refresh(UUID tenantId, UUID userProfileId, String platform) {
        this.tenantId = tenantId;
        this.userProfileId = userProfileId;
        this.platform = platform;
        this.lastSeenAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getUserProfileId() { return userProfileId; }
    public String getPlatform() { return platform; }
    public String getToken() { return token; }
    public String getTokenHash() { return tokenHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
}
