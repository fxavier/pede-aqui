package com.delivery.marketing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Request used to create a new time-bounded promotion. */
public record CreatePromotionRequest(
        @NotBlank String name,
        String description,
        @NotBlank String discountType,
        @NotNull @DecimalMin("0.01") BigDecimal discountValue,
        UUID vendorId,
        @NotBlank String appliesTo,
        @NotNull Instant startsAt,
        Instant endsAt) {}
