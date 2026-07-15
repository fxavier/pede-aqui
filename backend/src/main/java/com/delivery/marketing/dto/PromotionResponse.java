package com.delivery.marketing.dto;

import com.delivery.marketing.entity.PromotionScope;
import com.delivery.marketing.entity.PromotionStatus;
import com.delivery.marketing.entity.PromotionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Promotion data returned to API clients (spec-002 Promotion schema). */
public record PromotionResponse(
        UUID id,
        UUID vendorId,
        String name,
        String code,
        PromotionType type,
        BigDecimal value,
        PromotionScope scope,
        UUID targetCategoryId,
        UUID targetProductId,
        BigDecimal minOrderTotal,
        BigDecimal maxDiscountAmount,
        Instant startsAt,
        Instant endsAt,
        Integer usageLimit,
        Integer perCustomerLimit,
        int usedCount,
        PromotionStatus status,
        Instant createdAt,
        Instant updatedAt) {}
