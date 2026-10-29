package com.delivery.marketing.api;

import java.util.UUID;

/**
 * Checkout seam for spec-002 promotions: resolves the single discount to apply inside the checkout transaction.
 * Owned by marketing since ADR-0002 W3-A (C3, was {@code order.service}): order depends on marketing through this
 * interface, and marketing no longer sees order or cart types.
 */
public interface CheckoutDiscountResolver {

    /**
     * Resolves the effective discount for the basket being checked out (coupon wins over automatic
     * promotions; limit checks and usage accounting happen inside the checkout transaction).
     * Never returns null; return {@link CheckoutDiscount#none()} when no discount applies.
     */
    CheckoutDiscount resolve(PromotionBasket basket, UUID customerId);
}
