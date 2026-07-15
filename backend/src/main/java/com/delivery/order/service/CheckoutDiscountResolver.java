package com.delivery.order.service;

import com.delivery.cart.entity.Cart;
import java.util.UUID;

/** Checkout seam for spec-002 promotions: resolves the single discount to apply inside the checkout transaction. */
public interface CheckoutDiscountResolver {

    /**
     * Resolves the effective discount for the cart being checked out (coupon wins over automatic
     * promotions; limit checks and usage accounting happen inside the checkout transaction).
     * Never returns null; return {@link CheckoutDiscount#none()} when no discount applies.
     */
    CheckoutDiscount resolve(Cart cart, UUID customerId);
}
