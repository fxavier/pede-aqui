package com.delivery.marketing.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Neutral view of what promotions are evaluated against (ADR-0002 C3 "PricingInput"): tenant, vendor, customer,
 * subtotal, the attached coupon code and the priced lines. Defined by marketing so it never imports the cart or
 * order types; the cart entity implements it directly (no copying, so the values are always the cart's own).
 */
public interface PromotionBasket {

    UUID getTenantId();

    UUID getVendorId();

    UUID getCustomerId();

    /** Sum of the line totals before fees, taxes and discounts. */
    BigDecimal getSubtotal();

    /** Coupon code attached to the basket, or null. */
    String getCouponCode();

    List<? extends Line> getItems();

    /** One priced line: the SKU and its total (unit price x quantity). */
    interface Line {
        UUID getSkuId();

        BigDecimal lineTotal();
    }
}
