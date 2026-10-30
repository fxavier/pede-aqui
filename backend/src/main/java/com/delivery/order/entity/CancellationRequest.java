package com.delivery.order.entity;

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
 * A customer's request to cancel an order the vendor already accepted (LAC-26, V059). Created REQUESTED with the
 * quoted fee; OPS/ADMIN approve it (the order is cancelled with the fee withheld from the refund) or reject it; an
 * open request whose order moved on is closed as REJECTED with a {@link CancellationRequestClosure}. At most one open
 * request per order (partial unique index).
 */
@Entity
@Table(name = "cancellation_requests")
public class CancellationRequest {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CancellationRequestStatus status;
    @Enumerated(EnumType.STRING)
    @Column(name = "closure_reason")
    private CancellationRequestClosure closureReason;
    /** The customer's own words (bounded); never copied to the history or the audit log. */
    @Column(updatable = false)
    private String reason;
    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;
    @Column(name = "decided_by")
    private String decidedBy;
    @Column(name = "fee_rate", nullable = false, updatable = false)
    private BigDecimal feeRate;
    @Column(name = "fee_amount", nullable = false)
    private BigDecimal feeAmount;
    @Column(name = "refund_amount")
    private BigDecimal refundAmount;
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;
    @Column(name = "decided_at")
    private Instant decidedAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected CancellationRequest() {}

    public CancellationRequest(UUID tenantId, UUID orderId, UUID requestedBy, String reason, BigDecimal feeRate, BigDecimal feeAmount) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.orderId = orderId;
        this.requestedBy = requestedBy;
        this.reason = reason;
        this.feeRate = feeRate;
        this.feeAmount = feeAmount;
        this.status = CancellationRequestStatus.REQUESTED;
        this.requestedAt = Instant.now();
        this.updatedAt = requestedAt;
    }

    /** True while OPS has not decided and the order has not moved on. */
    public boolean isOpen() { return status == CancellationRequestStatus.REQUESTED; }

    /** Approval: records the fee actually withheld and the refund requested. */
    public void approve(String decidedBy, BigDecimal appliedFee, BigDecimal refundAmount) {
        this.status = CancellationRequestStatus.APPROVED;
        this.feeAmount = appliedFee;
        this.refundAmount = refundAmount;
        decide(decidedBy);
    }

    /** Rejection by OPS, or automatic closure because the order moved on. */
    public void close(CancellationRequestClosure closure, String decidedBy) {
        this.status = CancellationRequestStatus.REJECTED;
        this.closureReason = closure;
        decide(decidedBy);
    }

    private void decide(String decidedBy) {
        this.decidedBy = decidedBy;
        this.decidedAt = Instant.now();
        this.updatedAt = decidedAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getOrderId() { return orderId; }
    public CancellationRequestStatus getStatus() { return status; }
    public CancellationRequestClosure getClosureReason() { return closureReason; }
    public String getReason() { return reason; }
    public UUID getRequestedBy() { return requestedBy; }
    public String getDecidedBy() { return decidedBy; }
    public BigDecimal getFeeRate() { return feeRate; }
    public BigDecimal getFeeAmount() { return feeAmount; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getDecidedAt() { return decidedAt; }
}
