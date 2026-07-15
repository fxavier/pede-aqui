package com.delivery.catalog.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** SKU data exposed for product purchase options; pendingPrice signals an in-flight moderation. */
public record SkuResponse(UUID id, String skuCode, String name, BigDecimal price, BigDecimal pendingPrice, boolean active) {}
