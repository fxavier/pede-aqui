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
 * A failed delivery under review (LAC-44, W7-B, V070). Opened OPEN by FAILED_DELIVERY (one per delivery) with the coded
 * reason, the reporter, the optional proof photo/note, the courier's last position as arrival evidence and the
 * default outcome of the configured policy; resolved once by OPS/ADMIN, who may override the default with a reason.
 * The resolution stores the final cost bearer/outcome/goods disposition and the money figures posted to the ledger.
 */
@Entity
@Table(name = "delivery_incidents")
public class DeliveryIncident {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;
    @Column(name = "delivery_id", nullable = false, updatable = false)
    private UUID deliveryId;
    @Column(name = "courier_id", updatable = false)
    private UUID courierId;
    @Column(nullable = false, updatable = false)
    private String reason;
    /** The reporter's own words (bounded); never copied to logs or the audit log. */
    @Column(updatable = false)
    private String note;
    @Column(name = "proof_photo_storage_key", updatable = false)
    private String proofPhotoStorageKey;
    @Column(name = "reported_by", nullable = false, updatable = false)
    private String reportedBy;
    @Column(name = "reported_by_role", nullable = false, updatable = false)
    private String reportedByRole;
    @Column(name = "failed_from_status", nullable = false, updatable = false)
    private String failedFromStatus;
    @Column(name = "evidence_latitude", updatable = false)
    private Double evidenceLatitude;
    @Column(name = "evidence_longitude", updatable = false)
    private Double evidenceLongitude;
    @Column(name = "evidence_accuracy_m", updatable = false)
    private Double evidenceAccuracyMeters;
    @Column(name = "evidence_recorded_at", updatable = false)
    private Instant evidenceRecordedAt;
    @Column(name = "evidence_distance_m", updatable = false)
    private Double evidenceDistanceMeters;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryIncidentStatus status;
    @Enumerated(EnumType.STRING)
    @Column(name = "default_cost_bearer", nullable = false, updatable = false)
    private IncidentCostBearer defaultCostBearer;
    @Enumerated(EnumType.STRING)
    @Column(name = "default_outcome", nullable = false, updatable = false)
    private IncidentOutcome defaultOutcome;
    @Enumerated(EnumType.STRING)
    @Column(name = "default_goods_disposition", nullable = false, updatable = false)
    private IncidentGoodsDisposition defaultGoodsDisposition;
    @Enumerated(EnumType.STRING)
    @Column(name = "cost_bearer")
    private IncidentCostBearer costBearer;
    @Enumerated(EnumType.STRING)
    @Column(name = "final_outcome")
    private IncidentOutcome finalOutcome;
    @Enumerated(EnumType.STRING)
    @Column(name = "goods_disposition")
    private IncidentGoodsDisposition goodsDisposition;
    @Column(nullable = false)
    private boolean overridden;
    @Column(name = "override_reason")
    private String overrideReason;
    @Column(name = "refund_amount")
    private BigDecimal refundAmount;
    @Column(name = "retained_fee_amount")
    private BigDecimal retainedFeeAmount;
    @Column(name = "merchant_charge_amount")
    private BigDecimal merchantChargeAmount;
    @Column(name = "platform_compensation_amount")
    private BigDecimal platformCompensationAmount;
    @Column(name = "decided_by")
    private String decidedBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected DeliveryIncident() {}

    /** Default outcome of the policy for one reason. */
    public record Decision(IncidentCostBearer costBearer, IncidentOutcome outcome, IncidentGoodsDisposition goodsDisposition) {}

    /** Arrival evidence: the courier's last position and its straight-line distance to the delivery address. */
    public record Evidence(Double latitude, Double longitude, Double accuracyMeters, Instant recordedAt, Double distanceMeters) {
        public static final Evidence NONE = new Evidence(null, null, null, null, null);
    }

    /** Resolved money figures (MZN, scale 2). */
    public record Amounts(BigDecimal refund, BigDecimal retainedFee, BigDecimal merchantCharge, BigDecimal platformCompensation) {}

    public DeliveryIncident(UUID tenantId, UUID orderId, UUID deliveryId, UUID courierId, String reason, String note,
                            String proofPhotoStorageKey, String reportedBy, String reportedByRole, String failedFromStatus,
                            Evidence evidence, Decision defaults) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.orderId = orderId;
        this.deliveryId = deliveryId;
        this.courierId = courierId;
        this.reason = reason;
        this.note = note;
        this.proofPhotoStorageKey = proofPhotoStorageKey;
        this.reportedBy = reportedBy;
        this.reportedByRole = reportedByRole;
        this.failedFromStatus = failedFromStatus;
        Evidence e = evidence == null ? Evidence.NONE : evidence;
        this.evidenceLatitude = e.latitude();
        this.evidenceLongitude = e.longitude();
        this.evidenceAccuracyMeters = e.accuracyMeters();
        this.evidenceRecordedAt = e.recordedAt();
        this.evidenceDistanceMeters = e.distanceMeters();
        this.status = DeliveryIncidentStatus.OPEN;
        this.defaultCostBearer = defaults.costBearer();
        this.defaultOutcome = defaults.outcome();
        this.defaultGoodsDisposition = defaults.goodsDisposition();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** Records the OPS decision once (OPEN → RESOLVED). */
    public void resolve(Decision decision, boolean overridden, String overrideReason, Amounts amounts, String decidedBy) {
        if (status != DeliveryIncidentStatus.OPEN) {
            throw new IllegalStateException("incident already resolved");
        }
        this.costBearer = decision.costBearer();
        this.finalOutcome = decision.outcome();
        this.goodsDisposition = decision.goodsDisposition();
        this.overridden = overridden;
        this.overrideReason = overrideReason;
        this.refundAmount = amounts.refund();
        this.retainedFeeAmount = amounts.retainedFee();
        this.merchantChargeAmount = amounts.merchantCharge();
        this.platformCompensationAmount = amounts.platformCompensation();
        this.decidedBy = decidedBy;
        this.status = DeliveryIncidentStatus.RESOLVED;
        this.resolvedAt = Instant.now();
        this.updatedAt = this.resolvedAt;
    }

    public boolean isOpen() { return status == DeliveryIncidentStatus.OPEN; }
    public Decision defaultDecision() { return new Decision(defaultCostBearer, defaultOutcome, defaultGoodsDisposition); }
    /** The final decision; null while OPEN. */
    public Decision finalDecision() { return isOpen() ? null : new Decision(costBearer, finalOutcome, goodsDisposition); }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getOrderId() { return orderId; }
    public UUID getDeliveryId() { return deliveryId; }
    public UUID getCourierId() { return courierId; }
    public String getReason() { return reason; }
    public String getNote() { return note; }
    public String getProofPhotoStorageKey() { return proofPhotoStorageKey; }
    public String getReportedBy() { return reportedBy; }
    public String getReportedByRole() { return reportedByRole; }
    public String getFailedFromStatus() { return failedFromStatus; }
    public Double getEvidenceLatitude() { return evidenceLatitude; }
    public Double getEvidenceLongitude() { return evidenceLongitude; }
    public Double getEvidenceAccuracyMeters() { return evidenceAccuracyMeters; }
    public Instant getEvidenceRecordedAt() { return evidenceRecordedAt; }
    public Double getEvidenceDistanceMeters() { return evidenceDistanceMeters; }
    public DeliveryIncidentStatus getStatus() { return status; }
    public IncidentCostBearer getDefaultCostBearer() { return defaultCostBearer; }
    public IncidentOutcome getDefaultOutcome() { return defaultOutcome; }
    public IncidentGoodsDisposition getDefaultGoodsDisposition() { return defaultGoodsDisposition; }
    public IncidentCostBearer getCostBearer() { return costBearer; }
    public IncidentOutcome getFinalOutcome() { return finalOutcome; }
    public IncidentGoodsDisposition getGoodsDisposition() { return goodsDisposition; }
    public boolean isOverridden() { return overridden; }
    public String getOverrideReason() { return overrideReason; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public BigDecimal getRetainedFeeAmount() { return retainedFeeAmount; }
    public BigDecimal getMerchantChargeAmount() { return merchantChargeAmount; }
    public BigDecimal getPlatformCompensationAmount() { return platformCompensationAmount; }
    public String getDecidedBy() { return decidedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}
