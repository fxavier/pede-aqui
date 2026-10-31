package com.delivery.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A payment mismatch for OPS/FINANCE (LAC-32, V068 {@code payment_reconciliation_cases}). Inserted idempotently by
 * {@code dedup_key} through JDBC ({@code PaymentReconciliationCaseRepository.openIfAbsent}); JPA only reads and resolves.
 */
@Entity
@Table(name = "payment_reconciliation_cases")
public class PaymentReconciliationCase {
    /** Kind of mismatch. */
    public enum Type {
        /** The PSP took the money but the payment could not be confirmed (expired/cancelled order): refund/reverse. */
        SUCCEEDED_NOT_CAPTURED,
        /** A second successful attempt for an already captured payment: the customer paid twice. */
        DUPLICATE_SUCCESS,
        /** The PSP reported another amount than the attempt's: the payment was not confirmed. */
        AMOUNT_MISMATCH,
        /** UNKNOWN for longer than {@code app.payments.attempts.unknown-review-after}: manual review with the PSP. */
        ATTEMPT_UNRESOLVED,
        /** A payment confirmed by a provider with no SUCCEEDED attempt behind it. */
        CONFIRMED_WITHOUT_ATTEMPT
    }

    /** Case lifecycle. */
    public enum Status { OPEN, RESOLVED }

    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Enumerated(EnumType.STRING)
    @Column(name = "case_type", nullable = false)
    private Type caseType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;
    @Column(name = "dedup_key", nullable = false)
    private String dedupKey;
    @Column(name = "payment_id")
    private UUID paymentId;
    @Column(name = "attempt_id")
    private UUID attemptId;
    @Column(name = "order_id")
    private UUID orderId;
    private String provider;
    @Column(name = "expected_amount")
    private BigDecimal expectedAmount;
    @Column(name = "observed_amount")
    private BigDecimal observedAmount;
    @Column(name = "detail_code")
    private String detailCode;
    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    @Column(name = "resolved_by")
    private String resolvedBy;
    @Column(name = "resolution_note")
    private String resolutionNote;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected PaymentReconciliationCase() {}

    /** Closes the case once (a repeat keeps the first resolution). Returns false when it was already resolved. */
    public boolean resolve(String actor, String note, Instant now) {
        if (status == Status.RESOLVED) {
            return false;
        }
        this.status = Status.RESOLVED;
        this.resolvedBy = actor;
        this.resolutionNote = note;
        this.resolvedAt = now;
        this.updatedAt = now;
        return true;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public Type getCaseType() { return caseType; }
    public Status getStatus() { return status; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getAttemptId() { return attemptId; }
    public UUID getOrderId() { return orderId; }
    public String getProvider() { return provider; }
    public BigDecimal getExpectedAmount() { return expectedAmount; }
    public BigDecimal getObservedAmount() { return observedAmount; }
    public String getDetailCode() { return detailCode; }
    public Instant getDetectedAt() { return detectedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getResolutionNote() { return resolutionNote; }
    public Instant getCreatedAt() { return createdAt; }
}
