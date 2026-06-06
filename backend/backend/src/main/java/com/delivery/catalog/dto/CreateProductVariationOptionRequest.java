package com.delivery.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateProductVariationOptionRequest(
    @NotNull UUID groupId,
    @NotBlank String name,
    String description,
    BigDecimal priceModifier,
    @NotNull Boolean available,
    @Min(0) int displayOrder
) {}