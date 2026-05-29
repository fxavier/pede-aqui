package com.delivery.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/** Request used to add a purchasable SKU to an existing product. */
public record CreateSkuRequest(
        @NotNull UUID productId,
        @NotNull UUID vendorId,
        @NotBlank String skuCode,
        @NotBlank String name,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        @Min(0) int initialStock) {}
