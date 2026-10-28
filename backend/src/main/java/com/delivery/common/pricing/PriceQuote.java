package com.delivery.common.pricing;

import java.math.BigDecimal;

/**
 * Server-side price quote before discounts: subtotal + delivery fee + service fee + taxes. Discounts
 * (spec-002 promotions/coupons) are applied on top by the cart/checkout through {@link PricingMath#orderTotal}.
 */
public record PriceQuote(
        BigDecimal subtotal,
        BigDecimal deliveryFee,
        BigDecimal serviceFee,
        BigDecimal taxes,
        BigDecimal total,
        BigDecimal serviceFeeRate,
        BigDecimal taxRate,
        DeliveryFeeQuote delivery,
        String engineVersion) {

    /** Delivery + service fee, the value stored in the legacy carts.fees / orders.fees columns. */
    public BigDecimal fees() {
        return deliveryFee.add(serviceFee);
    }
}
