package com.delivery.geo.api;

import com.delivery.common.pricing.DeliveryFeeQuote;
import com.delivery.common.pricing.GeoPoint;
import com.delivery.vendor.entity.Vendor;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Delivery fee per vendor for vendor search. Declared by geo and implemented by the cart module's single pricing
 * engine ({@code cart.service.PricingService}) so the fee shown in search is the one charged, without geo depending
 * on cart (ADR-0002 W3-A, cycle cart-geo: the engine itself reads geo's delivery_fee_rules). Synchronous, read-only.
 */
public interface DeliveryFeeEstimator {

    /**
     * Delivery fee per vendor id from {@code origin} (the customer's position; null = unknown, base fee estimate).
     */
    Map<UUID, DeliveryFeeQuote> estimateDeliveryFees(Collection<Vendor> vendors, GeoPoint origin);
}
