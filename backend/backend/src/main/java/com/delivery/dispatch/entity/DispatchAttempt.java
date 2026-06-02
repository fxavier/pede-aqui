package com.delivery.dispatch.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dispatch_attempts")
public class DispatchAttempt {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dispatch_job_id", nullable = false)
    private UUID dispatchJobId;

    @Column(name = "courier_id", nullable = false)
    private UUID courierId;

    @Column(nullable = false, length = 30)
    private String outcome;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    protected DispatchAttempt() {
    }

    public DispatchAttempt(UUID id, UUID tenantId, UUID dispatchJobId, UUID courierId, 
                          String outcome, Instant attemptedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.dispatchJobId = dispatchJobId;
        this.courierId = courierId;
        this.outcome = outcome;
        this.attemptedAt = attemptedAt;
    }

    public void setRespondedAt(Instant respondedAt) {
        this.respondedAt = respondedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getDispatchJobId() {
        return dispatchJobId;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public String getOutcome() {
        return outcome;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }
}