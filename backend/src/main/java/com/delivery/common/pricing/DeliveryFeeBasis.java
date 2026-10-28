package com.delivery.common.pricing;

/** Why a delivery fee has its value; persisted on the order quote (orders.quote_delivery_fee_basis). */
public enum DeliveryFeeBasis {
    /** Vendor distance band from delivery_fee_rules, using the server-side straight-line distance. */
    DISTANCE_RULE,
    /** Tenant base fee from fee_policies.delivery_fee: no distance known (missing coordinates) or the vendor has no rules. */
    POLICY_BASE,
    /** The tenant has no fee_policies row: built-in platform defaults (identical to the V048 seed). */
    PLATFORM_DEFAULT,
    /** Empty cart (subtotal 0): nothing is charged. */
    EMPTY_CART
}
