package com.delivery.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One pending price change row for the OPS/ADMIN moderation queue. */
public record PendingPriceChangeResponse(
        UUID skuId,
        UUID productId,
        String productName,
        UUID vendorId,
        BigDecimal currentPrice,
        BigDecimal pendingPrice,
        BigDecimal deltaPercent,
        String submittedBy,
        Instant submittedAt) {}
