package com.delivery.support.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Represents a customer support ticket linked to tenant business records. */
@Entity
@Table(name = "support_tickets")
public class SupportTicket {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "creator_user_id", nullable = false)
    private String creatorUserId;
    @Column(name = "order_id")
    private UUID orderId;
    @Column(nullable = false)
    private String subject;
    @Column(nullable = false)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupportTicketStatus status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentClassification classification;
    @Column(name = "internal_notes")
    private String internalNotes;
    @Column(name = "assignee_user_id")
    private String assigneeUserId;
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected SupportTicket() {}

    public SupportTicket(UUID id, UUID tenantId, String creatorUserId, UUID orderId, String subject, String description) {
        this.id = id;
        this.tenantId = tenantId;
        this.creatorUserId = creatorUserId;
        this.orderId = orderId;
        this.subject = subject;
        this.description = description;
        this.status = SupportTicketStatus.OPEN;
        this.classification = IncidentClassification.GENERAL;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void classify(IncidentClassification classification) { this.classification = classification; this.updatedAt = Instant.now(); }
    public void setStatus(SupportTicketStatus status) { this.status = status; this.updatedAt = Instant.now(); }
    public void addInternalNote(String note, String assigneeUserId) { this.internalNotes = note; this.assigneeUserId = assigneeUserId; this.updatedAt = Instant.now(); }
    public void resolve(String assigneeUserId) { this.status = SupportTicketStatus.RESOLVED; this.assigneeUserId = assigneeUserId; this.resolvedAt = Instant.now(); this.updatedAt = this.resolvedAt; }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getCreatorUserId() { return creatorUserId; }
    public UUID getOrderId() { return orderId; }
    public String getSubject() { return subject; }
    public String getDescription() { return description; }
    public SupportTicketStatus getStatus() { return status; }
    public IncidentClassification getClassification() { return classification; }
    public String getInternalNotes() { return internalNotes; }
    public String getAssigneeUserId() { return assigneeUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
