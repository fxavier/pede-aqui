package com.delivery.common.pricing;

import java.math.BigDecimal;
import java.util.List;

/**
 * Server-side price quote before discounts: subtotal + delivery fee + service fee + taxes. Discounts
 * (spec-002 promotions/coupons) are applied on top by the cart/checkout through {@link PricingMath#orderTotal}.
 *
 * <p>W6-A: with a VAT-inclusive policy ({@code taxInclusive}, the default) {@code taxes} — the tax line charged on
 * top — is zero and the VAT contained in goods and fees is given by {@link #vat(BigDecimal)}; with an exclusive
 * policy {@code taxes} is the VAT added on top (the pre-W6-A formula). {@code policyMaxDeliveryDistanceKm} is the
 * tenant's maximum delivery distance (null = no limit); a vendor-level value overrides it at checkout.
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
        String engineVersion,
        boolean taxInclusive,
        List<TaxableLine> lines,
        BigDecimal policyMaxDeliveryDistanceKm) {

    public PriceQuote {
        lines = List.copyOf(lines);
    }

    /** Delivery + service fee, the value stored in the legacy carts.fees / orders.fees columns. */
    public BigDecimal fees() {
        return deliveryFee.add(serviceFee);
    }

    /** VAT breakdown once {@code discountTotal} is applied (the charged total is recomputed from this quote). */
    public VatBreakdown vat(BigDecimal discountTotal) {
        BigDecimal discount = discountTotal == null ? BigDecimal.ZERO : discountTotal;
        BigDecimal charged = PricingMath.orderTotal(subtotal, fees(), taxes, discount);
        return VatBreakdown.of(taxInclusive, taxRate, lines, deliveryFee, serviceFee, discount, taxes, charged);
    }
}
