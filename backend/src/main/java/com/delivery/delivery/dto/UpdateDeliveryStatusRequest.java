package com.delivery.delivery.dto;

import com.delivery.delivery.api.DeliveryFailureReason;
import com.delivery.delivery.entity.DeliveryStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Request used to move delivery through courier lifecycle statuses. {@code failureReason} is required with
 * FAILED_DELIVERY and refused with any other status (LAC-44); {@code failureNote} is optional free text kept on the
 * delivery incident only.
 */
public record UpdateDeliveryStatusRequest(
        @NotNull DeliveryStatus status,
        String proofPhotoStorageKey,
        BigDecimal cashCollectedAmount,
        DeliveryFailureReason failureReason,
        @Size(max = 500) String failureNote) {

    /** Pre-LAC-44 shape (no failure reason). */
    public UpdateDeliveryStatusRequest(DeliveryStatus status, String proofPhotoStorageKey, BigDecimal cashCollectedAmount) {
        this(status, proofPhotoStorageKey, cashCollectedAmount, null, null);
    }
}
