package com.delivery.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Records sensitive admin and operations actions for traceability. */
@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "actor_user_id", nullable = false)
    private String actorUserId;
    @Column(nullable = false)
    private String action;
    @Column(name = "target_type", nullable = false)
    private String targetType;
    @Column(name = "target_id")
    private String targetId;
    @Column(name = "business_reference")
    private String businessReference;
    @Column(nullable = false)
    private String result;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditLog() {}

    public AuditLog(UUID id, UUID tenantId, String actorUserId, String action, String targetType, String targetId, String businessReference, String result) {
        this.id = id;
        this.tenantId = tenantId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.businessReference = businessReference;
        this.result = result;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getActorUserId() { return actorUserId; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getBusinessReference() { return businessReference; }
    public String getResult() { return result; }
    public Instant getCreatedAt() { return createdAt; }
}
