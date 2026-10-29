package com.delivery.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Records sensitive admin and operations actions for traceability. Append-only (LAC-15): {@link Immutable} keeps
 * Hibernate from ever issuing an UPDATE, and the V053 trigger rejects UPDATE/DELETE/TRUNCATE in the database.
 */
@Entity
@Immutable
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
    @Column(name = "ip")
    private String ip;
    @Column(name = "user_agent")
    private String userAgent;
    @Column(name = "correlation_id")
    private String correlationId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private Map<String, Object> details;

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

    /** Attaches request context and masked details before the first (and only) insert. */
    public AuditLog withContext(String ip, String userAgent, String correlationId, Map<String, Object> details) {
        this.ip = ip;
        this.userAgent = userAgent;
        this.correlationId = correlationId;
        this.details = details;
        return this;
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
    public String getIp() { return ip; }
    public String getUserAgent() { return userAgent; }
    public String getCorrelationId() { return correlationId; }
    public Map<String, Object> getDetails() { return details; }
}
