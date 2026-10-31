package com.delivery.marketing.mapper;

import com.delivery.marketing.dto.CouponResponse;
import com.delivery.marketing.dto.PromotionResponse;
import com.delivery.marketing.entity.Promotion;
import com.delivery.marketing.entity.PromotionStatus;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Converts marketing entities to response DTOs. */
@Component
public class MarketingMapper {

    /**
     * {@code ends_at} stored for a coupon without end date (legacy {@code validUntil = null}); the coupon façade and
     * V073 write it and the façade maps it back to {@code null}.
     */
    public static final Instant OPEN_ENDED_UNTIL = Instant.parse("2099-12-31T23:59:59Z");

    /**
     * Legacy coupon shape of a coupon promotion (LAC-29 façade): {@code active} = stored status ACTIVE (a paused coupon
     * is "inactive"; like the legacy flag it does not turn false on expiry), {@code validUntil} null when open-ended.
     */
    public CouponResponse toCouponResponse(Promotion promotion) {
        return new CouponResponse(
                promotion.getId(),
                promotion.getCode(),
                promotion.getType().name(),
                promotion.getValue(),
                promotion.getMinOrderTotal(),
                promotion.getUsageLimit(),
                promotion.getUsedCount(),
                promotion.getVendorId(),
                promotion.getStartsAt(),
                OPEN_ENDED_UNTIL.equals(promotion.getEndsAt()) ? null : promotion.getEndsAt(),
                promotion.getStatus() == PromotionStatus.ACTIVE,
                promotion.getCreatedAt());
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
