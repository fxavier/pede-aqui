package com.delivery.cart.controller;

import com.delivery.cart.dto.AddCartItemRequest;
import com.delivery.cart.dto.CartResponse;
import com.delivery.cart.dto.PricingResponse;
import com.delivery.cart.service.CartService;
import com.delivery.cart.service.PricingService;
import com.delivery.common.security.TenantContext;
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
    private final TenantContext tenantContext;

    public CartController(CartService cartService, PricingService pricingService, TenantContext tenantContext) {
        this.cartService = cartService;
        this.pricingService = pricingService;
        this.tenantContext = tenantContext;
    }

    @PostMapping("/items")
    public CartResponse addItem(@PathVariable UUID customerId, @Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(request.vendorId(), request.skuId(), request.quantity());
    }

    /**
     * Preview of fees and tax for a subtotal with the tenant's fee policy (no address: base delivery fee). A
     * {@code couponCode} parameter is ignored since INC-13 (the "MVP10" magic coupon was removed); coupons are
     * applied with POST /api/v1/cart/{cartId}/coupon.
     */
    @GetMapping("/pricing")
    public PricingResponse price(@RequestParam BigDecimal subtotal) {
        return pricingService.preview(tenantContext.currentTenantId().orElse(null), subtotal);
    }
}
