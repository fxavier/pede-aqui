package com.delivery.privacy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A data subject's account deletion request (LAC-19, V071): PENDING during the reflection period (cancellable), then
 * ERASED once the database is pseudonymised and COMPLETED once the Keycloak user is deleted; or CANCELLED.
 */
@Entity
@Table(name = "account_deletion_requests")
public class AccountDeletionRequest {
    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "keycloak_user_id", nullable = false)
    private String keycloakUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountDeletionStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "scheduled_for", nullable = false)
    private Instant scheduledFor;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "erased_at")
    private Instant erasedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected AccountDeletionRequest() {
    }

    public AccountDeletionRequest(UUID id, UUID tenantId, UUID profileId, String keycloakUserId, Instant requestedAt, Instant scheduledFor) {
        this.id = id;
        this.tenantId = tenantId;
        this.profileId = profileId;
        this.keycloakUserId = keycloakUserId;
        this.status = AccountDeletionStatus.PENDING;
        this.requestedAt = requestedAt;
        this.scheduledFor = scheduledFor;
        this.updatedAt = requestedAt;
    }

    /** PENDING -> CANCELLED (the caller checked the status). */
    public void cancel(Instant now) {
        this.status = AccountDeletionStatus.CANCELLED;
        this.cancelledAt = now;
        this.lastError = null;
        this.updatedAt = now;
    }

    /** PENDING -> ERASED: the database side of the account is pseudonymised. */
    public void markErased(Instant now) {
        this.status = AccountDeletionStatus.ERASED;
        this.erasedAt = now;
        this.lastError = null;
        this.updatedAt = now;
    }

    /** ERASED -> COMPLETED: the Keycloak user is gone too. */
    public void markCompleted(Instant now) {
        this.status = AccountDeletionStatus.COMPLETED;
        this.completedAt = now;
        this.lastError = null;
        this.updatedAt = now;
    }

    /** Records a failed or postponed job attempt with a stable error code (never personal data). */
    public void recordAttemptFailure(String errorCode, Instant now) {
        this.attempts++;
        this.lastError = errorCode;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProfileId() { return profileId; }
    public String getKeycloakUserId() { return keycloakUserId; }
    public AccountDeletionStatus getStatus() { return status; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getScheduledFor() { return scheduledFor; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getErasedAt() { return erasedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
}
