package com.delivery.dispatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "courier_documents")
public class CourierDocument {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "courier_id", nullable = false)
    private UUID courierId;
    @Column(name = "document_type", nullable = false)
    private String documentType;
    @Column(name = "storage_key", nullable = false)
    private String storageKey;
    @Column(nullable = false)
    private String status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CourierDocument() {}

    public CourierDocument(UUID id, UUID tenantId, UUID courierId, String documentType, String storageKey) {
        this.id = id;
        this.tenantId = tenantId;
        this.courierId = courierId;
        this.documentType = documentType;
        this.storageKey = storageKey;
        this.status = "PENDING";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getCourierId() { return courierId; }
    public String getDocumentType() { return documentType; }
    public String getStorageKey() { return storageKey; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}