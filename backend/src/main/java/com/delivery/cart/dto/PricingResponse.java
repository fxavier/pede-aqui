package com.delivery.cart.dto;

import java.math.BigDecimal;

/** Pricing response containing simple MVP totals. */
public record PricingResponse(BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal serviceFee, BigDecimal tax, BigDecimal discount, BigDecimal total) {}
