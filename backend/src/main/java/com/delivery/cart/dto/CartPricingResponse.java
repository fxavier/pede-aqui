package com.delivery.cart.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Cart pricing snapshot returned by the coupon endpoints (spec-002 CartPricing schema). */
public record CartPricingResponse(
        UUID cartId,
        BigDecimal subtotal,
        BigDecimal fees,
        BigDecimal taxes,
        BigDecimal discountTotal,
        BigDecimal total,
        UUID appliedPromotionId) {}
