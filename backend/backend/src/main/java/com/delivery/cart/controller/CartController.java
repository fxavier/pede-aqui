package com.delivery.cart.controller;

import com.delivery.cart.dto.AddCartItemRequest;
import com.delivery.cart.dto.CartResponse;
import com.delivery.cart.dto.PricingResponse;
import com.delivery.cart.service.CartService;
import com.delivery.cart.service.PricingService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exposes cart item and pricing endpoints for customers. */
@RestController
@RequestMapping("/api/v1/customers/{customerId}/cart")
public class CartController {
    private final CartService cartService;
    private final PricingService pricingService;

    public CartController(CartService cartService, PricingService pricingService) { this.cartService = cartService; this.pricingService = pricingService; }

    @PostMapping("/items")
    public CartResponse addItem(@PathVariable UUID customerId, @Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(customerId, request.vendorId(), request.skuId(), request.quantity());
    }

    @GetMapping("/pricing")
    public PricingResponse price(@RequestParam BigDecimal subtotal, @RequestParam(required = false) String couponCode) {
        return pricingService.calculate(subtotal, couponCode);
    }
}
