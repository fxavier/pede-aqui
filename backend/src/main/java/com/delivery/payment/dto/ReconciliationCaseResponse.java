package com.delivery.payment.dto;

import com.delivery.payment.entity.PaymentReconciliationCase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A payment reconciliation case for OPS/FINANCE (LAC-32). Ids and amounts only — no personal data. */
public record ReconciliationCaseResponse(UUID id, String caseType, String status, UUID paymentId, UUID attemptId, UUID orderId,
                                         String provider, BigDecimal expectedAmount, BigDecimal observedAmount, String detailCode,
                                         Instant detectedAt, Instant resolvedAt, String resolutionNote) {

    /** Maps the entity. */
    public static ReconciliationCaseResponse of(PaymentReconciliationCase c) {
        return new ReconciliationCaseResponse(c.getId(), c.getCaseType().name(), c.getStatus().name(), c.getPaymentId(),
                c.getAttemptId(), c.getOrderId(), c.getProvider(), c.getExpectedAmount(), c.getObservedAmount(), c.getDetailCode(),
                c.getDetectedAt(), c.getResolvedAt(), c.getResolutionNote());
    }
}
