package com.delivery.marketing.service;

import com.delivery.cart.entity.Cart;
import com.delivery.common.exception.BusinessException;
import com.delivery.marketing.entity.Promotion;
import com.delivery.marketing.entity.PromotionRedemption;
import com.delivery.marketing.repository.PromotionRedemptionRepository;
import com.delivery.marketing.repository.PromotionRepository;
import com.delivery.order.service.CheckoutDiscount;
import com.delivery.order.service.CheckoutDiscountResolver;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Spec-002 implementation of the checkout discount seam: re-validates the attached coupon (or picks
 * the best automatic promotion), atomically consumes a usage slot via the conditional UPDATE, and
 * writes the promotion_redemption row inside the same checkout transaction (AC-7.6). Carts without
 * promotions resolve to none() so legacy checkout flows are untouched.
 */
@Component
@Primary
public class CheckoutPromotionResolver implements CheckoutDiscountResolver {
    private final PromotionResolver promotionResolver;
    private final PromotionRepository promotionRepository;
    private final PromotionRedemptionRepository redemptionRepository;

    public CheckoutPromotionResolver(PromotionResolver promotionResolver,
                                     PromotionRepository promotionRepository,
                                     PromotionRedemptionRepository redemptionRepository) {
        this.promotionResolver = promotionResolver;
        this.promotionRepository = promotionRepository;
        this.redemptionRepository = redemptionRepository;
    }

    /** Resolves the single effective discount and books usage atomically; limit breaches abort checkout. */
    @Override
    public CheckoutDiscount resolve(Cart cart, UUID customerId) {
        // Coupon wins over automatic; an attached-but-invalid coupon aborts checkout with a clear error
        // rather than silently dropping the discount the customer saw on the cart.
        Optional<PromotionResolver.ResolvedDiscount> resolved = cart.getCouponCode() != null
                ? Optional.of(promotionResolver.resolveCoupon(cart, cart.getCouponCode()))
                : promotionResolver.resolveBestAutomatic(cart);
        if (resolved.isEmpty()) {
            return CheckoutDiscount.none();
        }
        Promotion promotion = resolved.get().promotion();
        BigDecimal amount = resolved.get().amount();
        // Atomic usage booking: the conditional UPDATE row-locks the promotion; a racing checkout
        // re-evaluates the WHERE clause after the lock and gets 0 rows once the limit is consumed.
        int updated = promotionRepository.incrementUsageIfWithinLimit(promotion.getId());
        if (updated == 0) {
            throw new BusinessException("promotion_usage_exhausted",
                    "Promotion usage limit has been reached", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        scheduleRedemptionWrite(cart.getTenantId(), customerId, promotion.getId(), amount);
        return new CheckoutDiscount(promotion.getId(), amount);
    }

    /**
     * The order id does not exist yet when the seam runs, so the redemption row is written just
     * before commit of the same transaction: by then the order row (carrying applied_promotion_id)
     * is in the persistence context and is found by the no-redemption-yet lookup. Any failure here
     * rolls back the entire checkout, so usage is never consumed without a redemption.
     */
    private void scheduleRedemptionWrite(UUID tenantId, UUID customerId, UUID promotionId, BigDecimal amount) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Checkout discount resolution requires an active transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                writeRedemptions(tenantId, customerId, promotionId, amount);
            }
        });
    }

    /** Attaches the redemption to the order(s) created in this transaction for the promotion. */
    void writeRedemptions(UUID tenantId, UUID customerId, UUID promotionId, BigDecimal amount) {
        List<UUID> orderIds = redemptionRepository.findUnredeemedOrderIds(tenantId, customerId, promotionId);
        if (orderIds.isEmpty()) {
            throw new IllegalStateException("No order found to attach the promotion redemption to");
        }
        for (UUID orderId : orderIds) {
            redemptionRepository.save(new PromotionRedemption(UUID.randomUUID(), tenantId, promotionId, customerId, orderId, amount));
        }
    }
}
