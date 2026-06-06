package com.delivery.catalog.dto;

import java.util.List;
import java.util.UUID;

public record ProductVariationGroupResponse(
    UUID id,
    UUID productId,
    String name,
    String description,
    boolean required,
    int minSelections,
    int maxSelections,
    int displayOrder,
    List<ProductVariationOptionResponse> options
) {}