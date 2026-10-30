package com.delivery.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request used to convert an active cart into an order. {@code addressId} (one of the caller's saved addresses)
 * is required by the current request version (INC-12); requests without it are the deprecated legacy shape,
 * accepted only during the compatibility window documented in docs/api/API-MIGRATION.md. {@code quoteId}
 * (optional, W6-A) is the id returned by GET /checkout/quote: with it checkout charges exactly that quote or answers
 * 409 quote_changed (expired, or prices/fees moved) with a fresh quote; without it the pre-W6-A revalidation applies.
 */
public record CheckoutRequest(@NotNull UUID cartId, @NotBlank String idempotencyKey, UUID addressId, UUID quoteId) {

    /** Pre-W6-A shape (no quoteId), kept for callers and tests. */
    public CheckoutRequest(UUID cartId, String idempotencyKey, UUID addressId) {
        this(cartId, idempotencyKey, addressId, null);
    }
}
