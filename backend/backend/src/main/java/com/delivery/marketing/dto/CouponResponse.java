package com.delivery.marketing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Coupon data returned to API clients. */
public record CouponResponse(
        UUID id,
        String code,
        String discountType,
        BigDecimal discountValue,
        BigDecimal minOrderAmount,
        Integer maxUses,
        int usesCount,
        UUID vendorId,
        Instant validFrom,
        Instant validUntil,
        boolean active,
        Instant createdAt) {}
