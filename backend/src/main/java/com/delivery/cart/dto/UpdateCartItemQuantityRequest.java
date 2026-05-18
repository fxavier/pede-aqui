package com.delivery.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request used to update the quantity of an item in the active cart. */
public record UpdateCartItemQuantityRequest(@NotNull UUID skuId, @Min(1) int quantity) {}
