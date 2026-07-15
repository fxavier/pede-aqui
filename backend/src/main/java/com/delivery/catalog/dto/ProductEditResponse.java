package com.delivery.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Product state returned by the backoffice edit endpoints (spec 002 ProductResponse shape). */
public record ProductEditResponse(
        UUID id,
        UUID vendorId,
        UUID categoryId,
        String name,
        String description,
        String status,
        boolean requiresPrescription,
        String imageUrl,
        BigDecimal price,
        BigDecimal pendingPrice,
        Instant updatedAt) {}
