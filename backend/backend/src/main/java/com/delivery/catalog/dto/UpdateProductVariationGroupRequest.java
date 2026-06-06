package com.delivery.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateProductVariationGroupRequest(
    @NotBlank String name,
    String description,
    @NotNull Boolean required,
    @Min(0) int minSelections,
    @Min(1) int maxSelections,
    @Min(0) int displayOrder
) {}