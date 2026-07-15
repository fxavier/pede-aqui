package com.delivery.marketing.mapper;

import com.delivery.marketing.dto.CouponResponse;
import com.delivery.marketing.dto.PromotionResponse;
import com.delivery.marketing.entity.Coupon;
import com.delivery.marketing.entity.Promotion;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Converts marketing entities to response DTOs. */
@Component
public class MarketingMapper {

    public CouponResponse toCouponResponse(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getCode(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getMinOrderAmount(),
                coupon.getMaxUses(),
                coupon.getUsesCount(),
                coupon.getVendorId(),
                coupon.getValidFrom(),
                coupon.getValidUntil(),
                coupon.isActive(),
                coupon.getCreatedAt());
    }

    /** Maps a promotion to its API shape, resolving EXPIRED from the validity window (AC-7.3). */
    public PromotionResponse toPromotionResponse(Promotion promotion) {
        return new PromotionResponse(
                promotion.getId(),
                promotion.getVendorId(),
                promotion.getName(),
                promotion.getCode(),
                promotion.getType(),
                promotion.getValue(),
                promotion.getScope(),
                promotion.getTargetCategoryId(),
                promotion.getTargetProductId(),
                promotion.getMinOrderTotal(),
                promotion.getMaxDiscountAmount(),
                promotion.getStartsAt(),
                promotion.getEndsAt(),
                promotion.getUsageLimit(),
                promotion.getPerCustomerLimit(),
                promotion.getUsedCount(),
                promotion.effectiveStatus(Instant.now()),
                promotion.getCreatedAt(),
                promotion.getUpdatedAt());
    }
}
