package com.delivery.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Single-SKU price change payload; price must be strictly positive MZN. */
public record UpdatePriceRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal price) {}
