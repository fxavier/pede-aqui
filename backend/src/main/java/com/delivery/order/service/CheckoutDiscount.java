package com.delivery.order.service;

import java.math.BigDecimal;
import java.util.UUID;

/** Value object for the discount resolved at checkout: applied promotion id (nullable) and amount. */
public record CheckoutDiscount(UUID appliedPromotionId, BigDecimal discountTotal) {

    private static final CheckoutDiscount NONE = new CheckoutDiscount(null, BigDecimal.ZERO);

    /** Returns the no-discount result (no promotion, amount 0). */
    public static CheckoutDiscount none() {
        return NONE;
    }
}
