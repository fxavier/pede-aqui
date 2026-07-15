package com.delivery.marketing.service;

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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Computes the single effective discount for a cart (no stacking): an explicit coupon wins over
 * automatic promotions; eligibility covers status, validity window, vendor scope, min-order gate,
 * ORDER/CATEGORY/PRODUCT targeting, percentage cap, and usage-limit headroom (spec 002 US-7).
 */
@Service
public class PromotionResolver {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private final PromotionRepository promotionRepository;
    private final PromotionRedemptionRepository redemptionRepository;
    private final SkuRepository skuRepository;
    private final ProductRepository productRepository;

    public PromotionResolver(PromotionRepository promotionRepository,
                             PromotionRedemptionRepository redemptionRepository,
                             SkuRepository skuRepository, ProductRepository productRepository) {
        this.promotionRepository = promotionRepository;
        this.redemptionRepository = redemptionRepository;
        this.skuRepository = skuRepository;
        this.productRepository = productRepository;
    }

    /** A qualifying promotion together with the discount amount it grants for the cart. */
    public record ResolvedDiscount(Promotion promotion, BigDecimal amount) {}

    /** Resolves a coupon code against the cart; throws 422 when invalid, expired, or not eligible (AC-7.5). */
    public ResolvedDiscount resolveCoupon(Cart cart, String rawCode) {
        String code = PromotionService.normalizeCode(rawCode);
        if (code == null) {
            throw ineligible("coupon_code_required", "A coupon code is required");
        }
        Promotion promotion = promotionRepository.findByTenantIdAndCode(cart.getTenantId(), code)
                .orElseThrow(() -> ineligible("coupon_not_found", "Coupon code is not valid"));
        Instant now = Instant.now();
        if (promotion.effectiveStatus(now) == PromotionStatus.EXPIRED) {
            throw ineligible("coupon_expired", "Coupon has expired");
        }
        if (promotion.getStatus() != PromotionStatus.ACTIVE || !promotion.isWithinWindow(now)) {
            throw ineligible("coupon_not_active", "Coupon is not currently active");
        }
        if (!appliesToVendor(promotion, cart)) {
            throw ineligible("coupon_not_eligible", "Coupon does not apply to this vendor");
        }
        if (!meetsMinOrder(promotion, cart)) {
            throw ineligible("coupon_min_order_not_met", "Cart subtotal is below the coupon minimum order total");
        }
        if (!promotion.hasGlobalUsageHeadroom()) {
            throw ineligible("coupon_usage_exhausted", "Coupon usage limit has been reached");
        }
        if (isPerCustomerLimitReached(promotion, cart.getCustomerId())) {
            throw ineligible("coupon_per_customer_limit_reached", "Coupon has already been used the maximum number of times");
        }
        BigDecimal amount = discountAmount(promotion, cart);
        if (amount.signum() <= 0) {
            throw ineligible("coupon_not_eligible", "Coupon does not apply to any item in this cart");
        }
        return new ResolvedDiscount(promotion, amount);
    }

    /** Resolves the best-qualifying automatic (code-less) ACTIVE promotion for the cart, if any (AC-7.6). */
    public Optional<ResolvedDiscount> resolveBestAutomatic(Cart cart) {
        Instant now = Instant.now();
        return promotionRepository.findByTenantIdAndStatusAndCodeIsNull(cart.getTenantId(), PromotionStatus.ACTIVE).stream()
                .filter(p -> p.isWithinWindow(now))
                .filter(p -> appliesToVendor(p, cart))
                .filter(p -> meetsMinOrder(p, cart))
                .filter(Promotion::hasGlobalUsageHeadroom)
                .filter(p -> !isPerCustomerLimitReached(p, cart.getCustomerId()))
                .map(p -> new ResolvedDiscount(p, discountAmount(p, cart)))
                .filter(r -> r.amount().signum() > 0)
                .max(Comparator.comparing(ResolvedDiscount::amount));
    }

    /** Vendor-scoped promotions only apply to that vendor's cart; tenant-wide apply to all. */
    private boolean appliesToVendor(Promotion promotion, Cart cart) {
        return promotion.getVendorId() == null || promotion.getVendorId().equals(cart.getVendorId());
    }

    private boolean meetsMinOrder(Promotion promotion, Cart cart) {
        return promotion.getMinOrderTotal() == null || cart.getSubtotal().compareTo(promotion.getMinOrderTotal()) >= 0;
    }

    private boolean isPerCustomerLimitReached(Promotion promotion, UUID customerId) {
        return promotion.getPerCustomerLimit() != null
                && redemptionRepository.countByPromotionIdAndCustomerId(promotion.getId(), customerId) >= promotion.getPerCustomerLimit();
    }

    /** Computes the discount over the scope-eligible base, applying the percentage cap and clamping to the base. */
    private BigDecimal discountAmount(Promotion promotion, Cart cart) {
        BigDecimal base = eligibleBase(promotion, cart);
        if (base.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal amount;
        if (promotion.getType() == PromotionType.PERCENTAGE) {
            amount = base.multiply(promotion.getValue()).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
            if (promotion.getMaxDiscountAmount() != null && amount.compareTo(promotion.getMaxDiscountAmount()) > 0) {
                amount = promotion.getMaxDiscountAmount();
            }
        } else {
            amount = promotion.getValue();
        }
        // A discount can never exceed the eligible base (keeps totals non-negative).
        if (amount.compareTo(base) > 0) {
            amount = base;
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /** The cart amount a promotion can discount: whole subtotal, or the targeted category/product line totals. */
    private BigDecimal eligibleBase(Promotion promotion, Cart cart) {
        if (promotion.getScope() == PromotionScope.ORDER) {
            return cart.getSubtotal();
        }
        Map<UUID, UUID> productBySkuId = productBySkuId(cart);
        if (promotion.getScope() == PromotionScope.PRODUCT) {
            return lineTotalsWhere(cart, item -> promotion.getTargetProductId().equals(productBySkuId.get(item.getSkuId())));
        }
        Map<UUID, UUID> categoryByProductId = categoryByProductId(cart, productBySkuId);
        return lineTotalsWhere(cart, item -> promotion.getTargetCategoryId()
                .equals(categoryByProductId.get(productBySkuId.get(item.getSkuId()))));
    }

    private BigDecimal lineTotalsWhere(Cart cart, java.util.function.Predicate<CartItem> matches) {
        return cart.getItems().stream()
                .filter(matches)
                .map(CartItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<UUID, UUID> productBySkuId(Cart cart) {
        Set<UUID> skuIds = cart.getItems().stream().map(CartItem::getSkuId).collect(Collectors.toSet());
        return skuRepository.findAllById(skuIds).stream()
                .collect(Collectors.toMap(Sku::getId, Sku::getProductId));
    }

    private Map<UUID, UUID> categoryByProductId(Cart cart, Map<UUID, UUID> productBySkuId) {
        return productRepository.findAllById(Set.copyOf(productBySkuId.values())).stream()
                .filter(product -> product.getCategoryId() != null)
                .collect(Collectors.toMap(Product::getId, Product::getCategoryId));
    }

    private static BusinessException ineligible(String code, String message) {
        return new BusinessException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
