package com.delivery.marketing.mapper;

import com.delivery.marketing.dto.CouponResponse;
import com.delivery.marketing.dto.PromotionResponse;
import com.delivery.marketing.entity.Coupon;
import com.delivery.marketing.entity.Promotion;
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

    public PromotionResponse toPromotionResponse(Promotion promotion) {
        return new PromotionResponse(
                promotion.getId(),
                promotion.getName(),
                promotion.getDescription(),
                promotion.getDiscountType(),
                promotion.getDiscountValue(),
                promotion.getVendorId(),
                promotion.getAppliesTo(),
                promotion.getStartsAt(),
                promotion.getEndsAt(),
                promotion.isActive(),
                promotion.getCreatedAt());
    }
}
