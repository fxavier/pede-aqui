package com.delivery.catalog.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductVariationOptionResponse(
    UUID id,
    UUID groupId,
    String name,
    String description,
    BigDecimal priceModifier,
    boolean available,
    int displayOrder
) {}