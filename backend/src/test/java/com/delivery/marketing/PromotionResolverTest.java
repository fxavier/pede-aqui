package com.delivery.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.cart.entity.Cart;
import com.delivery.cart.entity.CartItem;
import com.delivery.catalog.entity.Product;
import com.delivery.catalog.entity.Sku;
import com.delivery.catalog.repository.ProductRepository;
import com.delivery.catalog.repository.SkuRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.marketing.entity.Promotion;
import com.delivery.marketing.entity.PromotionScope;
import com.delivery.marketing.entity.PromotionStatus;
import com.delivery.marketing.entity.PromotionType;
import com.delivery.marketing.repository.PromotionRedemptionRepository;
import com.delivery.marketing.repository.PromotionRepository;
import com.delivery.marketing.service.PromotionResolver;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** D7 — resolver matrix: eligibility, min-order gate, percentage cap, scope targeting, expired/paused ignored. */
class PromotionResolverTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID otherProductId = UUID.randomUUID();
    private final UUID skuId = UUID.randomUUID();
    private final UUID otherSkuId = UUID.randomUUID();

    private final PromotionRepository promotionRepository = mock(PromotionRepository.class);
    private final PromotionRedemptionRepository redemptionRepository = mock(PromotionRedemptionRepository.class);
    private final SkuRepository skuRepository = mock(SkuRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final PromotionResolver resolver =
            new PromotionResolver(promotionRepository, redemptionRepository, skuRepository, productRepository);

    // Cart: sku (product/category above) 2 x 30.00 = 60.00 + otherSku 1 x 40.00 = 100.00 subtotal
    private Cart cart() {
        Cart cart = new Cart(UUID.randomUUID(), tenantId, customerId, vendorId);
        cart.addItem(new CartItem(UUID.randomUUID(), cart, tenantId, skuId, "P1", "P1", new BigDecimal("30.00"), 2));
        cart.addItem(new CartItem(UUID.randomUUID(), cart, tenantId, otherSkuId, "P2", "P2", new BigDecimal("40.00"), 1));
        return cart;
    }

    private void stubCatalog() {
        when(skuRepository.findAllById(anyCollection())).thenReturn(List.of(
                new Sku(skuId, tenantId, productId, "SKU-1", "P1", new BigDecimal("30.00")),
                new Sku(otherSkuId, tenantId, otherProductId, "SKU-2", "P2", new BigDecimal("40.00"))));
        when(productRepository.findAllById(anyCollection())).thenReturn(List.of(
                new Product(productId, tenantId, vendorId, categoryId, "P1", null),
                new Product(otherProductId, tenantId, vendorId, UUID.randomUUID(), "P2", null)));
    }

    private Promotion promotion(String code, PromotionType type, BigDecimal value, PromotionScope scope,
                                UUID targetCategoryId, UUID targetProductId) {
        Promotion promotion = new Promotion(UUID.randomUUID(), tenantId, null, "Promo", code, type, value, scope,
                targetCategoryId, targetProductId, null, null,
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS), null, null);
        promotion.activate();
        return promotion;
    }

    private void stubCoupon(Promotion promotion) {
        when(promotionRepository.findByTenantIdAndCode(tenantId, promotion.getCode())).thenReturn(Optional.of(promotion));
    }

    @Test
    void orderScopePercentageCouponDiscountsSubtotal() {
        Promotion promo = promotion("SAVE10", PromotionType.PERCENTAGE, new BigDecimal("10"), PromotionScope.ORDER, null, null);
        stubCoupon(promo);

        PromotionResolver.ResolvedDiscount resolved = resolver.resolveCoupon(cart(), "save10");

        assertThat(resolved.amount()).isEqualByComparingTo("10.00");
        assertThat(resolved.promotion().getId()).isEqualTo(promo.getId());
    }

    @Test
    void percentageCapLimitsDiscount() {
        Promotion promo = promotion("BIG50", PromotionType.PERCENTAGE, new BigDecimal("50"), PromotionScope.ORDER, null, null);
        ReflectionTestUtils.setField(promo, "maxDiscountAmount", new BigDecimal("15.00"));
        stubCoupon(promo);

        assertThat(resolver.resolveCoupon(cart(), "BIG50").amount()).isEqualByComparingTo("15.00");
    }

    @Test
    void fixedAmountIsClampedToEligibleBase() {
        Promotion promo = promotion("HUGE", PromotionType.FIXED_AMOUNT, new BigDecimal("500.00"), PromotionScope.ORDER, null, null);
        stubCoupon(promo);

        assertThat(resolver.resolveCoupon(cart(), "HUGE").amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void minOrderGateRejectsCoupon() {
        Promotion promo = promotion("MIN200", PromotionType.PERCENTAGE, new BigDecimal("10"), PromotionScope.ORDER, null, null);
        ReflectionTestUtils.setField(promo, "minOrderTotal", new BigDecimal("200.00"));
        stubCoupon(promo);

        assertThatThrownBy(() -> resolver.resolveCoupon(cart(), "MIN200"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "coupon_min_order_not_met");
    }

    @Test
    void minOrderGateExcludesAutomaticPromotion() {
        Promotion promo = promotion(null, PromotionType.PERCENTAGE, new BigDecimal("10"), PromotionScope.ORDER, null, null);
        ReflectionTestUtils.setField(promo, "minOrderTotal", new BigDecimal("200.00"));
        when(promotionRepository.findByTenantIdAndStatusAndCodeIsNull(tenantId, PromotionStatus.ACTIVE)).thenReturn(List.of(promo));

        assertThat(resolver.resolveBestAutomatic(cart())).isEmpty();
    }

    @Test
    void productScopeDiscountsOnlyTargetedLines() {
        stubCatalog();
        Promotion promo = promotion("PROD", PromotionType.PERCENTAGE, new BigDecimal("10"), PromotionScope.PRODUCT, null, productId);
        stubCoupon(promo);

        // Only the 2 x 30.00 lines of the targeted product qualify: 10% of 60.00
        assertThat(resolver.resolveCoupon(cart(), "PROD").amount()).isEqualByComparingTo("6.00");
    }

    @Test
    void categoryScopeDiscountsOnlyTargetedLines() {
        stubCatalog();
        Promotion promo = promotion("CAT", PromotionType.FIXED_AMOUNT, new BigDecimal("100.00"), PromotionScope.CATEGORY, categoryId, null);
        stubCoupon(promo);

        // Fixed amount clamps to the targeted category base of 60.00
        assertThat(resolver.resolveCoupon(cart(), "CAT").amount()).isEqualByComparingTo("60.00");
    }

    @Test
    void scopeWithNoMatchingItemsIsIneligible() {
        stubCatalog();
        Promotion promo = promotion("NOPE", PromotionType.PERCENTAGE, new BigDecimal("10"), PromotionScope.PRODUCT, null, UUID.randomUUID());
        stubCoupon(promo);

        assertThatThrownBy(() -> resolver.resolveCoupon(cart(), "NOPE"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "coupon_not_eligible");
    }

    @Test
    void expiredCouponIsRejected() {
        Promotion promo = promotion("OLD", PromotionType.PERCENTAGE, new BigDecimal("10"), PromotionScope.ORDER, null, null);
        ReflectionTestUtils.setField(promo, "startsAt", Instant.now().minus(10, ChronoUnit.DAYS));
        ReflectionTestUtils.setField(promo, "endsAt", Instant.now().minus(1, ChronoUnit.DAYS));
        stubCoupon(promo);

        assertThatThrownBy(() -> resolver.resolveCoupon(cart(), "OLD"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "coupon_expired");
    }

    @Test
    void pausedCouponIsRejected() {
        Promotion promo = promotion("PAUSED", PromotionType.PERCENTAGE, new BigDecimal("10"), PromotionScope.ORDER, null, null);
        promo.pause();
        stubCoupon(promo);

        assertThatThrownBy(() -> resolver.resolveCoupon(cart(), "PAUSED"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "coupon_not_active");
    }

    @Test
    void expiredAutomaticPromotionIsIgnored() {
        Promotion promo = promotion(null, PromotionType.PERCENTAGE, new BigDecimal("10"), PromotionScope.ORDER, null, null);
        ReflectionTestUtils.setField(promo, "startsAt", Instant.now().minus(10, ChronoUnit.DAYS));
        ReflectionTestUtils.setField(promo, "endsAt", Instant.now().minus(1, ChronoUnit.DAYS));
        when(promotionRepository.findByTenantIdAndStatusAndCodeIsNull(tenantId, PromotionStatus.ACTIVE)).thenReturn(List.of(promo));

        assertThat(resolver.resolveBestAutomatic(cart())).isEmpty();
    }

    @Test
    void vendorScopedCouponRejectsOtherVendorsCart() {
        Promotion promo = promotion("VND", PromotionType.PERCENTAGE, new BigDecimal("10"), PromotionScope.ORDER, null, null);
        ReflectionTestUtils.setField(promo, "vendorId", UUID.randomUUID());
        stubCoupon(promo);

        assertThatThrownBy(() -> resolver.resolveCoupon(cart(), "VND"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "coupon_not_eligible");
    }

    @Test
    void bestAutomaticPromotionWins() {
        Promotion small = promotion(null, PromotionType.FIXED_AMOUNT, new BigDecimal("5.00"), PromotionScope.ORDER, null, null);
        Promotion big = promotion(null, PromotionType.PERCENTAGE, new BigDecimal("20"), PromotionScope.ORDER, null, null);
        when(promotionRepository.findByTenantIdAndStatusAndCodeIsNull(tenantId, PromotionStatus.ACTIVE)).thenReturn(List.of(small, big));

        Optional<PromotionResolver.ResolvedDiscount> resolved = resolver.resolveBestAutomatic(cart());

        assertThat(resolved).isPresent();
        assertThat(resolved.get().promotion().getId()).isEqualTo(big.getId());
        assertThat(resolved.get().amount()).isEqualByComparingTo("20.00");
    }

    @Test
    void perCustomerLimitBlocksCoupon() {
        Promotion promo = promotion("ONCE", PromotionType.PERCENTAGE, new BigDecimal("10"), PromotionScope.ORDER, null, null);
        ReflectionTestUtils.setField(promo, "perCustomerLimit", 1);
        stubCoupon(promo);
        when(redemptionRepository.countByPromotionIdAndCustomerId(promo.getId(), customerId)).thenReturn(1L);

        assertThatThrownBy(() -> resolver.resolveCoupon(cart(), "ONCE"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "coupon_per_customer_limit_reached");
    }

    @Test
    void exhaustedGlobalUsageBlocksCoupon() {
        Promotion promo = promotion("FULL", PromotionType.PERCENTAGE, new BigDecimal("10"), PromotionScope.ORDER, null, null);
        ReflectionTestUtils.setField(promo, "usageLimit", 2);
        ReflectionTestUtils.setField(promo, "usedCount", 2);
        stubCoupon(promo);

        assertThatThrownBy(() -> resolver.resolveCoupon(cart(), "FULL"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "coupon_usage_exhausted");
    }

    @Test
    void unknownCouponCodeIsRejected() {
        when(promotionRepository.findByTenantIdAndCode(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveCoupon(cart(), "MISSING"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "coupon_not_found");
    }
}
