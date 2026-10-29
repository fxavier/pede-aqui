package com.delivery.tenant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Records one idempotency-keyed tenant-provisioning attempt from the subscriptions platform. */
@Entity
@Table(name = "provisioning_requests")
public class ProvisioningRequest {
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "owner_email", nullable = false)
    private String ownerEmail;

    @Column(name = "external_ref")
    private String externalRef;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false)
    private String status;

    @Column(name = "response_json")
    private String responseJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProvisioningRequest() {
    }

    public ProvisioningRequest(UUID id, String idempotencyKey, String ownerEmail, String externalRef, String status) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.ownerEmail = ownerEmail;
        this.externalRef = externalRef;
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void markSucceeded(UUID tenantId, String responseJson) {
        this.status = STATUS_SUCCEEDED;
        this.tenantId = tenantId;
        this.responseJson = responseJson;
        this.updatedAt = Instant.now();
    }

    public void markFailed() {
        this.status = STATUS_FAILED;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getOwnerEmail() { return ownerEmail; }
    public String getExternalRef() { return externalRef; }
    public UUID getTenantId() { return tenantId; }
    public String getStatus() { return status; }
    public String getResponseJson() { return responseJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
