package com.delivery.marketing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Promotion data returned to API clients. */
public record PromotionResponse(
        UUID id,
        String name,
        String description,
        String discountType,
        BigDecimal discountValue,
        UUID vendorId,
        String appliesTo,
        Instant startsAt,
        Instant endsAt,
        boolean active,
        Instant createdAt) {}
