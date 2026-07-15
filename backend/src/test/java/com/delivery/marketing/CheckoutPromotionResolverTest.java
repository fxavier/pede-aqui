package com.delivery.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.cart.entity.Cart;
import com.delivery.common.exception.BusinessException;
import com.delivery.marketing.entity.Promotion;
import com.delivery.marketing.entity.PromotionRedemption;
import com.delivery.marketing.entity.PromotionScope;
import com.delivery.marketing.entity.PromotionType;
import com.delivery.marketing.repository.PromotionRedemptionRepository;
import com.delivery.marketing.repository.PromotionRepository;
import com.delivery.marketing.service.CheckoutPromotionResolver;
import com.delivery.marketing.service.PromotionResolver;
import com.delivery.order.service.CheckoutDiscount;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * D8 — checkout seam behaviour: coupon-wins-over-automatic, atomic usage booking via the
 * conditional UPDATE (0 rows → abort, no usage consumed), and the pre-commit redemption write.
 */
class CheckoutPromotionResolverTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();

    private final PromotionResolver promotionResolver = mock(PromotionResolver.class);
    private final PromotionRepository promotionRepository = mock(PromotionRepository.class);
    private final PromotionRedemptionRepository redemptionRepository = mock(PromotionRedemptionRepository.class);
    private final CheckoutPromotionResolver resolver =
            new CheckoutPromotionResolver(promotionResolver, promotionRepository, redemptionRepository);

    @BeforeEach
    void openFakeTransaction() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void closeFakeTransaction() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private Promotion promotion(String code, Integer usageLimit) {
        return new Promotion(UUID.randomUUID(), tenantId, null, "Promo", code, PromotionType.PERCENTAGE,
                new BigDecimal("10"), PromotionScope.ORDER, null, null, null, null,
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS), usageLimit, null);
    }

    private Cart cartWithCoupon(String code, UUID promotionId) {
        Cart cart = new Cart(UUID.randomUUID(), tenantId, customerId, vendorId);
        if (code != null) {
            cart.applyPromotion(promotionId, code, new BigDecimal("10.00"));
        }
        return cart;
    }

    @Test
    void couponWinsOverAutomaticPromotions() {
        Promotion coupon = promotion("SAVE10", null);
        Cart cart = cartWithCoupon("SAVE10", coupon.getId());
        when(promotionResolver.resolveCoupon(cart, "SAVE10"))
                .thenReturn(new PromotionResolver.ResolvedDiscount(coupon, new BigDecimal("10.00")));
        when(promotionRepository.incrementUsageIfWithinLimit(coupon.getId())).thenReturn(1);

        CheckoutDiscount discount = resolver.resolve(cart, customerId);

        assertThat(discount.appliedPromotionId()).isEqualTo(coupon.getId());
        assertThat(discount.discountTotal()).isEqualByComparingTo("10.00");
        verify(promotionResolver, never()).resolveBestAutomatic(any());
    }

    @Test
    void legacyCartWithoutPromotionsResolvesToNone() {
        Cart cart = cartWithCoupon(null, null);
        when(promotionResolver.resolveBestAutomatic(cart)).thenReturn(Optional.empty());

        CheckoutDiscount discount = resolver.resolve(cart, customerId);

        assertThat(discount.appliedPromotionId()).isNull();
        assertThat(discount.discountTotal()).isEqualByComparingTo("0");
        verify(promotionRepository, never()).incrementUsageIfWithinLimit(any());
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void automaticPromotionAppliesWhenNoCouponAttached() {
        Promotion automatic = promotion(null, null);
        Cart cart = cartWithCoupon(null, null);
        when(promotionResolver.resolveBestAutomatic(cart))
                .thenReturn(Optional.of(new PromotionResolver.ResolvedDiscount(automatic, new BigDecimal("7.50"))));
        when(promotionRepository.incrementUsageIfWithinLimit(automatic.getId())).thenReturn(1);

        CheckoutDiscount discount = resolver.resolve(cart, customerId);

        assertThat(discount.appliedPromotionId()).isEqualTo(automatic.getId());
        assertThat(discount.discountTotal()).isEqualByComparingTo("7.50");
    }

    @Test
    void usageLimitRaceOnlyOneCheckoutWins() {
        // Contract of the conditional UPDATE: with usage_limit=1 the first increment returns 1 row,
        // the racing second returns 0 rows and must abort without consuming usage or writing a redemption.
        Promotion limited = promotion("LAST1", 1);
        Cart winner = cartWithCoupon("LAST1", limited.getId());
        Cart loser = cartWithCoupon("LAST1", limited.getId());
        when(promotionResolver.resolveCoupon(any(Cart.class), any()))
                .thenReturn(new PromotionResolver.ResolvedDiscount(limited, new BigDecimal("10.00")));
        when(promotionRepository.incrementUsageIfWithinLimit(limited.getId())).thenReturn(1).thenReturn(0);
        UUID orderId = UUID.randomUUID();
        when(redemptionRepository.findUnredeemedOrderIds(tenantId, customerId, limited.getId()))
                .thenReturn(List.of(orderId));

        CheckoutDiscount first = resolver.resolve(winner, customerId);
        assertThat(first.appliedPromotionId()).isEqualTo(limited.getId());

        assertThatThrownBy(() -> resolver.resolve(loser, customerId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "promotion_usage_exhausted");

        // Only the winner registered a redemption write; simulate commit and assert exactly one row.
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);
        synchronizations.forEach(s -> s.beforeCommit(false));

        ArgumentCaptor<PromotionRedemption> captor = ArgumentCaptor.forClass(PromotionRedemption.class);
        verify(redemptionRepository, times(1)).save(captor.capture());
        PromotionRedemption redemption = captor.getValue();
        assertThat(redemption.getPromotionId()).isEqualTo(limited.getId());
        assertThat(redemption.getOrderId()).isEqualTo(orderId);
        assertThat(redemption.getCustomerId()).isEqualTo(customerId);
        assertThat(redemption.getAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void redemptionWriteFailsCommitWhenNoOrderCarriesThePromotion() {
        Promotion coupon = promotion("SAVE10", null);
        Cart cart = cartWithCoupon("SAVE10", coupon.getId());
        when(promotionResolver.resolveCoupon(cart, "SAVE10"))
                .thenReturn(new PromotionResolver.ResolvedDiscount(coupon, new BigDecimal("10.00")));
        when(promotionRepository.incrementUsageIfWithinLimit(coupon.getId())).thenReturn(1);
        when(redemptionRepository.findUnredeemedOrderIds(any(), any(), any())).thenReturn(List.of());

        resolver.resolve(cart, customerId);
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();

        assertThatThrownBy(() -> synchronizations.forEach(s -> s.beforeCommit(false)))
                .isInstanceOf(IllegalStateException.class);
        verify(redemptionRepository, never()).save(any());
    }

    @Test
    void resolvingOutsideATransactionIsRejected() {
        TransactionSynchronizationManager.clearSynchronization();
        try {
            Promotion coupon = promotion("SAVE10", null);
            Cart cart = cartWithCoupon("SAVE10", coupon.getId());
            when(promotionResolver.resolveCoupon(cart, "SAVE10"))
                    .thenReturn(new PromotionResolver.ResolvedDiscount(coupon, new BigDecimal("10.00")));
            when(promotionRepository.incrementUsageIfWithinLimit(coupon.getId())).thenReturn(1);

            assertThatThrownBy(() -> resolver.resolve(cart, customerId)).isInstanceOf(IllegalStateException.class);
        } finally {
            TransactionSynchronizationManager.initSynchronization();
        }
    }
}
