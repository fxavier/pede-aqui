package com.delivery.order.dto;

import com.delivery.order.entity.DeliveryIncidentStatus;
import com.delivery.order.entity.IncidentCostBearer;
import com.delivery.order.entity.IncidentGoodsDisposition;
import com.delivery.order.entity.IncidentOutcome;
import com.delivery.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A delivery incident as OPS/ADMIN/SUPPORT see it (LAC-44): the coded reason, reporter, proof and arrival evidence, the
 * policy default and — once resolved — the final decision, override and money figures. {@code resolution} is null
 * while OPEN.
 */
public record DeliveryIncidentResponse(UUID id, UUID orderId, String orderReference, OrderStatus orderStatus, UUID deliveryId,
                                       UUID courierId, String reason, String note, String proofPhotoStorageKey,
                                       String reportedByRole, String failedFromStatus, Evidence evidence,
                                       DeliveryIncidentStatus status, Decision defaultDecision, Resolution resolution,
                                       Instant createdAt) {

    /** Courier's last position at report time and its straight-line distance to the address (all null when unknown). */
    public record Evidence(Double latitude, Double longitude, Double accuracyMeters, Instant recordedAt, Double distanceMeters) {}

    /** One decision: who bears the cost, the refund outcome and what happens to the goods. */
    public record Decision(IncidentCostBearer costBearer, IncidentOutcome outcome, IncidentGoodsDisposition goodsDisposition) {}

    /** The OPS decision and the money figures it produced (MZN). */
    public record Resolution(Decision decision, boolean overridden, String overrideReason, BigDecimal refundAmount,
                             BigDecimal retainedFeeAmount, BigDecimal merchantChargeAmount, BigDecimal platformCompensationAmount,
                             String decidedBy, Instant resolvedAt) {}
}
