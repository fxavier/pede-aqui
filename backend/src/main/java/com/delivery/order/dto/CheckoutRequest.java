package com.delivery.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request used to convert an active cart into an order. {@code addressId} (one of the caller's saved addresses)
 * is required by the current request version (INC-12); requests without it are the deprecated legacy shape,
 * accepted only during the compatibility window documented in docs/api/API-MIGRATION.md.
 */
public record CheckoutRequest(@NotNull UUID cartId, @NotBlank String idempotencyKey, UUID addressId) {}
