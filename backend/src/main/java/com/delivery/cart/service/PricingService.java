package com.delivery.cart.service;

import com.delivery.cart.dto.PricingResponse;
import com.delivery.common.pricing.PricingMath;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/** Calculates simple MVP cart fees, tax, discounts, and totals. */
@Service
public class PricingService {
    private static final BigDecimal DELIVERY_FEE = new BigDecimal("5.00");
    private static final BigDecimal SERVICE_FEE_RATE = new BigDecimal("0.05");
    private static final BigDecimal TAX_RATE = new BigDecimal("0.08");

    /** Calculates deterministic totals for a cart subtotal and optional coupon. */
    public PricingResponse calculate(BigDecimal subtotal, String couponCode) {
        BigDecimal deliveryFee = subtotal.signum() == 0 ? BigDecimal.ZERO : DELIVERY_FEE;
        BigDecimal serviceFee = subtotal.multiply(SERVICE_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = subtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = "MVP10".equalsIgnoreCase(couponCode) ? subtotal.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal total = PricingMath.orderTotal(subtotal, deliveryFee.add(serviceFee), tax, discount);
        return new PricingResponse(subtotal, deliveryFee, serviceFee, tax, discount, total);
    }
}
