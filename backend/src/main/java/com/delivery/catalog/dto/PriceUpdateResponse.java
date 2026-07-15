package com.delivery.catalog.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Outcome of a price edit: applied in place (reviewRequired=false) or queued for moderation. */
public record PriceUpdateResponse(
        UUID skuId,
        BigDecimal currentPrice,
        BigDecimal pendingPrice,
        boolean reviewRequired) {}
