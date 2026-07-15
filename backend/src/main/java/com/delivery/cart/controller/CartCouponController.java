package com.delivery.cart.controller;

import com.delivery.cart.dto.ApplyCouponRequest;
import com.delivery.cart.dto.CartPricingResponse;
import com.delivery.cart.service.CartCouponService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes cart coupon apply/remove endpoints returning updated cart pricing (spec-002 AC-7.5). */
@RestController
@RequestMapping("/api/v1/cart/{cartId}/coupon")
public class CartCouponController {
    private final CartCouponService service;

    public CartCouponController(CartCouponService service) { this.service = service; }

    @PostMapping
    public CartPricingResponse apply(@PathVariable UUID cartId, @Valid @RequestBody ApplyCouponRequest request) {
        return service.applyCoupon(cartId, request.code());
    }

    @DeleteMapping
    public CartPricingResponse remove(@PathVariable UUID cartId) {
        return service.removeCoupon(cartId);
    }
}
