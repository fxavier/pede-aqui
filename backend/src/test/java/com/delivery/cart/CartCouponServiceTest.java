package com.delivery.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.cart.dto.CartPricingResponse;
import com.delivery.cart.entity.Cart;
import com.delivery.cart.entity.CartItem;
import com.delivery.cart.repository.CartRepository;
import com.delivery.cart.service.CartCouponService;
import com.delivery.cart.service.PricingService;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.marketing.entity.Promotion;
import com.delivery.marketing.entity.PromotionScope;
import com.delivery.marketing.entity.PromotionType;
import com.delivery.marketing.service.PromotionResolver;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** D4 — cart coupon apply/remove recomputes CartPricing and enforces ownership/eligibility. */
class CartCouponServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();
    private final UUID cartId = UUID.randomUUID();
    private final String keycloakUserId = "kc-user";

    private final CartRepository cartRepository = mock(CartRepository.class);
    private final PromotionResolver promotionResolver = mock(PromotionResolver.class);
    private final TenantContext tenantContext = mock(TenantContext.class);
    private final AppUserProfileRepository userProfileRepository = mock(AppUserProfileRepository.class);
    private final CartCouponService service = new CartCouponService(cartRepository, promotionResolver,
            new PricingService(), tenantContext, userProfileRepository);

    private Cart cart;

    @BeforeEach
    void setUp() {
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(tenantContext.currentKeycloakUserId()).thenReturn(Optional.of(keycloakUserId));
        when(userProfileRepository.findByTenantIdAndKeycloakUserId(tenantId, keycloakUserId))
                .thenReturn(Optional.of(new AppUserProfile(customerId, tenantId, keycloakUserId, "c@x.mz", "Customer", Set.of())));
        cart = new Cart(cartId, tenantId, customerId, vendorId);
        cart.addItem(new CartItem(UUID.randomUUID(), cart, tenantId, UUID.randomUUID(), "P1", "P1", new BigDecimal("100.00"), 1));
        when(cartRepository.findByTenantIdAndId(tenantId, cartId)).thenReturn(Optional.of(cart));
    }

    private Promotion coupon(String code) {
        Promotion promotion = new Promotion(UUID.randomUUID(), tenantId, null, "Promo", code,
                PromotionType.PERCENTAGE, new BigDecimal("10"), PromotionScope.ORDER, null, null, null, null,
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.DAYS), null, null);
        promotion.activate();
        return promotion;
    }

    @Test
    void applyCouponAttachesPromotionAndDiscountsPricing() {
        Promotion promo = coupon("SAVE10");
        when(promotionResolver.resolveCoupon(cart, "SAVE10"))
                .thenReturn(new PromotionResolver.ResolvedDiscount(promo, new BigDecimal("10.00")));

        CartPricingResponse pricing = service.applyCoupon(cartId, "SAVE10");

        // subtotal 100.00 → delivery 5.00 + service 5.00 = fees 10.00, tax 8.00, discount 10.00
        assertThat(pricing.subtotal()).isEqualByComparingTo("100.00");
        assertThat(pricing.fees()).isEqualByComparingTo("10.00");
        assertThat(pricing.taxes()).isEqualByComparingTo("8.00");
        assertThat(pricing.discountTotal()).isEqualByComparingTo("10.00");
        assertThat(pricing.total()).isEqualByComparingTo("108.00");
        assertThat(pricing.appliedPromotionId()).isEqualTo(promo.getId());
        assertThat(cart.getCouponCode()).isEqualTo("SAVE10");
    }

    @Test
    void removeCouponRestoresUndiscountedPricing() {
        cart.applyPromotion(UUID.randomUUID(), "SAVE10", new BigDecimal("10.00"));

        CartPricingResponse pricing = service.removeCoupon(cartId);

        assertThat(pricing.discountTotal()).isEqualByComparingTo("0");
        assertThat(pricing.total()).isEqualByComparingTo("118.00");
        assertThat(pricing.appliedPromotionId()).isNull();
        assertThat(cart.getCouponCode()).isNull();
    }

    @Test
    void ineligibleCouponPropagates422WithoutMutatingCart() {
        when(promotionResolver.resolveCoupon(any(Cart.class), any()))
                .thenThrow(new BusinessException("coupon_not_found", "Coupon code is not valid", HttpStatus.UNPROCESSABLE_ENTITY));

        assertThatThrownBy(() -> service.applyCoupon(cartId, "BAD"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(cart.getAppliedPromotionId()).isNull();
    }

    @Test
    void foreignCustomersCartIsForbidden() {
        Cart foreign = new Cart(cartId, tenantId, UUID.randomUUID(), vendorId);
        when(cartRepository.findByTenantIdAndId(tenantId, cartId)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.applyCoupon(cartId, "SAVE10"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "cart_access_denied");
    }

    @Test
    void checkedOutCartRejectsCouponChanges() {
        cart.markCheckedOut();

        assertThatThrownBy(() -> service.removeCoupon(cartId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "cart_not_active");
    }
}
