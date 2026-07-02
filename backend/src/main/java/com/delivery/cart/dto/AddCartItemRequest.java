package com.delivery.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request used to add one SKU to the active cart. */
public record AddCartItemRequest(@NotNull UUID vendorId, @NotNull UUID skuId, @Min(1) int quantity) {}
