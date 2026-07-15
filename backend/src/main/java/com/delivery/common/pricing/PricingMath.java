package com.delivery.common.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Single authority for the pricing formula: total = subtotal + fees + taxes - discountTotal (MZN, 2 decimals). */
public final class PricingMath {

    private PricingMath() {}

    /** Computes the canonical order/cart total, scaled to 2 decimals with HALF_UP rounding. */
    public static BigDecimal orderTotal(BigDecimal subtotal, BigDecimal fees, BigDecimal taxes, BigDecimal discountTotal) {
        return subtotal.add(fees).add(taxes).subtract(discountTotal).setScale(2, RoundingMode.HALF_UP);
    }
}
