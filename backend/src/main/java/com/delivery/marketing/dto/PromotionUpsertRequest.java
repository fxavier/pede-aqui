package com.delivery.marketing.dto;

import com.delivery.marketing.entity.PromotionScope;
import com.delivery.marketing.entity.PromotionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Create/update payload for a promotion, matching the spec-002 PromotionUpsert contract. */
public record PromotionUpsertRequest(
        UUID vendorId,
        @NotBlank @Size(max = 140) String name,
        @Size(max = 40) String code,
        @NotNull PromotionType type,
        @NotNull BigDecimal value,
        @NotNull PromotionScope scope,
        UUID targetCategoryId,
        UUID targetProductId,
        BigDecimal minOrderTotal,
        BigDecimal maxDiscountAmount,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Min(1) Integer usageLimit,
        @Min(1) Integer perCustomerLimit) {}
