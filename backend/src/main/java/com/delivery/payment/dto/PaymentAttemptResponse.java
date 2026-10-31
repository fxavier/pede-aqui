package com.delivery.payment.dto;

import com.delivery.payment.entity.PaymentAttempt;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * State of a payment attempt (LAC-31): {@code status} is INITIATED, PENDING_CUSTOMER ("confirme no seu telemóvel"),
 * SUCCEEDED, FAILED, EXPIRED or UNKNOWN (still being resolved — do not retry). The phone is masked to its last 3 digits.
 */
public record PaymentAttemptResponse(UUID id, UUID paymentId, UUID orderId, String provider, String status, BigDecimal amount,
                                     String currency, String msisdnMasked, String errorCode, Instant createdAt, Instant updatedAt,
                                     Instant completedAt) {

    /** Maps the entity. */
    public static PaymentAttemptResponse of(PaymentAttempt a) {
        return new PaymentAttemptResponse(a.getId(), a.getPaymentId(), a.getOrderId(), a.getProvider(), a.getStatus().name(),
                a.getAmount(), a.getCurrency(), a.getMsisdnLast3() == null ? null : "*******" + a.getMsisdnLast3(), a.getErrorCode(),
                a.getCreatedAt(), a.getUpdatedAt(), a.getCompletedAt());
    }
}
