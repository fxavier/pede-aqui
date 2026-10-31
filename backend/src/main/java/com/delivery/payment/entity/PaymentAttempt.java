package com.delivery.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * One request to a payment provider to charge a payment (LAC-31/32, V068 {@code payment_attempts}). Status changes only
 * through {@link #transitionTo}, which enforces {@link PaymentAttemptStatus}. Stores only a hash and the last 3 digits
 * of the phone number.
 */
@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;
    @Column(name = "order_id", nullable = false)
    private UUID orderId;
    @Column(nullable = false)
    private String provider;
    @Column(name = "our_reference", nullable = false)
    private String ourReference;
    @Column(name = "provider_reference")
    private String providerReference;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentAttemptStatus status;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(nullable = false)
    private String currency;
    @Column(name = "msisdn_hash")
    private String msisdnHash;
    @Column(name = "msisdn_last3")
    private String msisdnLast3;
    @Column(name = "error_code")
    private String errorCode;
    @Column(name = "poll_count", nullable = false)
    private int pollCount;
    @Column(name = "next_poll_at")
    private Instant nextPollAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Version
    private long version;

    protected PaymentAttempt() {}

    public PaymentAttempt(UUID id, UUID tenantId, UUID paymentId, UUID orderId, String provider, String ourReference,
                          BigDecimal amount, String currency, String msisdnHash, String msisdnLast3, Instant now,
                          Instant firstPollAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.provider = provider;
        this.ourReference = ourReference;
        this.amount = amount;
        this.currency = currency;
        this.msisdnHash = msisdnHash;
        this.msisdnLast3 = msisdnLast3;
        this.status = PaymentAttemptStatus.INITIATED;
        this.createdAt = now;
        this.updatedAt = now;
        this.nextPollAt = firstPollAt;
    }

    /**
     * Moves to {@code target} when the state machine allows it; returns false (and changes nothing) for a repeat or a
     * disallowed move, e.g. a FAILED callback after SUCCEEDED. Final statuses stop polling.
     */
    public boolean transitionTo(PaymentAttemptStatus target, String errorCode, Instant now) {
        if (!status.canTransitionTo(target)) {
            return false;
        }
        this.status = target;
        this.errorCode = errorCode;
        this.updatedAt = now;
        if (!target.isOpen()) {
            this.completedAt = now;
            this.nextPollAt = null;
        }
        return true;
    }

    /** Records the provider's reference once known (first value wins). */
    public void recordProviderReference(String reference, Instant now) {
        if (this.providerReference == null && reference != null && !reference.isBlank()) {
            this.providerReference = reference;
            this.updatedAt = now;
        }
    }

    /** Schedules the next status query with exponential backoff {@code base × 2^polls}, capped at {@code max}. */
    public void schedulePoll(Instant now, Duration base, Duration max) {
        pollCount++;
        long factor = 1L << Math.min(pollCount, 20);
        Duration delay = base.multipliedBy(factor);
        this.nextPollAt = now.plus(delay.compareTo(max) > 0 ? max : delay);
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getOrderId() { return orderId; }
    public String getProvider() { return provider; }
    public String getOurReference() { return ourReference; }
    public String getProviderReference() { return providerReference; }
    public PaymentAttemptStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getMsisdnLast3() { return msisdnLast3; }
    public String getErrorCode() { return errorCode; }
    public int getPollCount() { return pollCount; }
    public Instant getNextPollAt() { return nextPollAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
