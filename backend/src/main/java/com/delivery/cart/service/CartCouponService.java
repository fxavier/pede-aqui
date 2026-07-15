package com.delivery.cart.service;

import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.cart.dto.CartPricingResponse;
import com.delivery.cart.dto.PricingResponse;
import com.delivery.cart.entity.Cart;
import com.delivery.cart.repository.CartRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.pricing.PricingMath;
import com.delivery.common.security.TenantContext;
import com.delivery.marketing.service.PromotionResolver;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies and removes cart coupons (spec-002 AC-7.5), recomputing cart pricing with the discount. */
@Service
public class CartCouponService {
    private final CartRepository cartRepository;
    private final PromotionResolver promotionResolver;
    private final PricingService pricingService;
    private final TenantContext tenantContext;
    private final AppUserProfileRepository userProfileRepository;

    public CartCouponService(CartRepository cartRepository, PromotionResolver promotionResolver,
                             PricingService pricingService, TenantContext tenantContext,
                             AppUserProfileRepository userProfileRepository) {
        this.cartRepository = cartRepository;
        this.promotionResolver = promotionResolver;
        this.pricingService = pricingService;
        this.tenantContext = tenantContext;
        this.userProfileRepository = userProfileRepository;
    }

    /** Validates the coupon against the cart and attaches it; 422 when invalid/expired/ineligible. */
    @Transactional
    public CartPricingResponse applyCoupon(UUID cartId, String code) {
        Cart cart = loadOwnedActiveCart(cartId);
        PromotionResolver.ResolvedDiscount resolved = promotionResolver.resolveCoupon(cart, code);
        cart.applyPromotion(resolved.promotion().getId(), resolved.promotion().getCode(), resolved.amount());
        recomputeTotals(cart);
        return toPricing(cart);
    }

    /** Detaches any applied coupon/promotion and restores undiscounted pricing. */
    @Transactional
    public CartPricingResponse removeCoupon(UUID cartId) {
        Cart cart = loadOwnedActiveCart(cartId);
        cart.clearPromotion();
        recomputeTotals(cart);
        return toPricing(cart);
    }

    /** Recomputes fees/taxes and the canonical total including the cart's pending discount. */
    private void recomputeTotals(Cart cart) {
        PricingResponse pricing = pricingService.calculate(cart.getSubtotal(), null);
        BigDecimal fees = pricing.deliveryFee().add(pricing.serviceFee());
        BigDecimal total = PricingMath.orderTotal(cart.getSubtotal(), fees, pricing.tax(), cart.getDiscountTotal());
        cart.updateTotals(fees, pricing.tax(), pricing.discount(), total);
    }

    private CartPricingResponse toPricing(Cart cart) {
        return new CartPricingResponse(cart.getId(), cart.getSubtotal(), cart.getFees(), cart.getTaxes(),
                cart.getDiscountTotal(), cart.getTotal(), cart.getAppliedPromotionId());
    }

    /** Loads a tenant-scoped ACTIVE cart owned by the current customer. */
    private Cart loadOwnedActiveCart(UUID cartId) {
        UUID tenantId = tenantId();
        Cart cart = cartRepository.findByTenantIdAndId(tenantId, cartId)
                .orElseThrow(() -> new NotFoundException("Cart was not found"));
        if (!cart.getCustomerId().equals(currentCustomerId(tenantId))) {
            throw new BusinessException("cart_access_denied", "Access to this cart is not permitted", HttpStatus.FORBIDDEN);
        }
        if (!"ACTIVE".equals(cart.getStatus())) {
            throw new BusinessException("cart_not_active", "Coupons can only be changed on an active cart", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return cart;
    }

    private UUID currentCustomerId(UUID tenantId) {
        String keycloakUserId = tenantContext.currentKeycloakUserId()
                .orElseThrow(() -> new BusinessException("user_required", "Authenticated user is required", HttpStatus.FORBIDDEN));
        AppUserProfile currentUser = userProfileRepository.findByTenantIdAndKeycloakUserId(tenantId, keycloakUserId)
                .orElseThrow(() -> new NotFoundException("User profile was not found"));
        return currentUser.getId();
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
