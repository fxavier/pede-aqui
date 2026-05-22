package com.delivery.delivery.dto;

import com.delivery.delivery.entity.DeliveryStatus;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Request used to move delivery through courier lifecycle statuses. */
public record UpdateDeliveryStatusRequest(
        @NotNull DeliveryStatus status,
        String proofPhotoStorageKey,
        BigDecimal cashCollectedAmount) {}
