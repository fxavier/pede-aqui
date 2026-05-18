package com.delivery.vendor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Stores vendor document metadata and storage references. */
@Entity
@Table(name = "vendor_documents")
public class VendorDocument {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;
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

    protected VendorDocument() {}

    public VendorDocument(UUID id, UUID tenantId, UUID vendorId, String documentType, String storageKey) {
        this.id = id;
        this.tenantId = tenantId;
        this.vendorId = vendorId;
        this.documentType = documentType;
        this.storageKey = storageKey;
        this.status = "PENDING";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getVendorId() { return vendorId; }
    public String getDocumentType() { return documentType; }
    public String getStorageKey() { return storageKey; }
    public String getStatus() { return status; }
}
