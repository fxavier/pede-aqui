package com.delivery.marketing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Request used to create a new discount coupon. */
public record CreateCouponRequest(
        @NotBlank String code,
        @NotBlank String discountType,
        @NotNull @DecimalMin("0.01") BigDecimal discountValue,
        BigDecimal minOrderAmount,
        @Min(1) Integer maxUses,
        UUID vendorId,
        @NotNull Instant validFrom,
        Instant validUntil) {}
