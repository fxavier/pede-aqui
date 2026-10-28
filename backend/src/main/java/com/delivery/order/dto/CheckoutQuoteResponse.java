package com.delivery.order.dto;

import com.delivery.common.pricing.DeliveryFeeBasis;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Checkout price quote (GET /api/v1/checkout/quote, INC-13): the fees checkout will charge for this cart and
 * address. distanceKm is the straight-line distance used, null when vendor or address has no coordinates
 * (deliveryFeeBasis then tells which fallback applied).
 */
public record CheckoutQuoteResponse(
        UUID cartId,
        UUID addressId,
        BigDecimal subtotal,
        BigDecimal deliveryFee,
        BigDecimal serviceFee,
        BigDecimal taxes,
        BigDecimal discountTotal,
        BigDecimal total,
        DeliveryFeeBasis deliveryFeeBasis,
        BigDecimal distanceKm) {}
