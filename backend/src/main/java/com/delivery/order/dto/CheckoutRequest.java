package com.delivery.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request used to convert an active cart into an order. */
public record CheckoutRequest(@NotNull UUID cartId, @NotBlank String idempotencyKey) {}
