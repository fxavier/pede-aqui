package com.delivery.privacy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One consent of a data subject to a purpose and text version (LAC-63, V071): granted, and possibly withdrawn later. */
@Entity
@Table(name = "consents")
public class Consent {
    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_profile_id", nullable = false)
    private UUID subjectProfileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConsentPurpose purpose;

    @Column(nullable = false)
    private String version;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    protected Consent() {
    }

    public Consent(UUID id, UUID tenantId, UUID subjectProfileId, ConsentPurpose purpose, String version, Instant grantedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.subjectProfileId = subjectProfileId;
        this.purpose = purpose;
        this.version = version;
        this.grantedAt = grantedAt;
    }

    /** Marks the consent withdrawn (kept as evidence of the earlier grant). */
    public void withdraw(Instant now) {
        this.withdrawnAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSubjectProfileId() { return subjectProfileId; }
    public ConsentPurpose getPurpose() { return purpose; }
    public String getVersion() { return version; }
    public Instant getGrantedAt() { return grantedAt; }
    public Instant getWithdrawnAt() { return withdrawnAt; }
}
