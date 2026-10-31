package com.delivery.marketing.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditActions;
import com.delivery.common.service.AuditLogService;
import com.delivery.marketing.dto.CouponResponse;
import com.delivery.marketing.dto.CreateCouponRequest;
import com.delivery.marketing.entity.Promotion;
import com.delivery.marketing.entity.PromotionScope;
import com.delivery.marketing.entity.PromotionStatus;
import com.delivery.marketing.entity.PromotionType;
import com.delivery.marketing.mapper.MarketingMapper;
import com.delivery.marketing.repository.PromotionRepository;
import com.delivery.vendor.repository.VendorRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Legacy {@code /marketing/coupons} contract as a façade over the spec-002 {@code promotion} table (LAC-29): a coupon is
 * a promotion with a code, ORDER scope and no per-customer limit, created ACTIVE. Same JSON shape and error codes as
 * the legacy endpoint; the legacy {@code coupons} table is no longer written (its rows were migrated by V073). Because
 * coupons are now real promotions, a coupon created here is redeemable at checkout through the unchanged
 * {@code CheckoutDiscountResolver}.
 */
@Service
public class MarketingService {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    /** promotion.code is VARCHAR(40) (the legacy coupons.code allowed 50). */
    static final int MAX_CODE_LENGTH = 40;
    private final PromotionRepository promotionRepository;
    private final VendorRepository vendorRepository;
    private final MarketingMapper mapper;
    private final TenantContext tenantContext;
    private final AuditLogService auditLogService;

    public MarketingService(PromotionRepository promotionRepository, VendorRepository vendorRepository, MarketingMapper mapper,
                            TenantContext tenantContext, AuditLogService auditLogService) {
        this.promotionRepository = promotionRepository;
        this.vendorRepository = vendorRepository;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
        this.auditLogService = auditLogService;
    }

    /** Creates an ACTIVE coupon promotion; 409 {@code duplicate_coupon_code} when the code is taken by any promotion. */
    @Transactional
    public CouponResponse createCoupon(CreateCouponRequest request) {
        UUID tenantId = tenantId();
        String code = PromotionService.normalizeCode(request.code());
        if (code != null && code.length() > MAX_CODE_LENGTH) {
            throw badRequest("invalid_coupon_code", "Coupon code must have at most 40 characters");
        }
        PromotionType type = couponType(request.discountType());
        validate(tenantId, request, type);
        if (code == null || promotionRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new BusinessException("duplicate_coupon_code", "A coupon with this code already exists", HttpStatus.CONFLICT);
        }
        Instant endsAt = request.validUntil() == null ? MarketingMapper.OPEN_ENDED_UNTIL : request.validUntil();
        Promotion promotion = new Promotion(UUID.randomUUID(), tenantId, request.vendorId(), couponName(code), code, type,
                request.discountValue(), PromotionScope.ORDER, null, null, request.minOrderAmount(), null,
                request.validFrom(), endsAt, request.maxUses(), null);
        promotion.activate();
        Promotion saved = promotionRepository.save(promotion);
        auditLogService.log(AuditActions.PROMOTION_CREATED, "promotion", saved.getId().toString(), code, "SUCCESS");
        return mapper.toCouponResponse(saved);
    }

    /** Coupon promotions of the tenant (any promotion with a code), newest first, capped at 200 (LAC-14). */
    @Transactional(readOnly = true)
    public List<CouponResponse> listCoupons() {
        return promotionRepository.findTop200ByTenantIdAndCodeIsNotNullOrderByCreatedAtDescIdDesc(tenantId()).stream()
                .map(mapper::toCouponResponse)
                .toList();
    }

    /** Pauses an ACTIVE coupon promotion; 409 {@code already_inactive} when it is not active, 404 when unknown. */
    @Transactional
    public CouponResponse deactivateCoupon(UUID id) {
        Promotion promotion = promotionRepository.findByTenantIdAndId(tenantId(), id)
                .filter(p -> p.getCode() != null)
                .orElseThrow(() -> new NotFoundException("Coupon not found"));
        if (promotion.getStatus() != PromotionStatus.ACTIVE) {
            throw new BusinessException("already_inactive", "Coupon is already inactive", HttpStatus.CONFLICT);
        }
        promotion.pause();
        auditLogService.log(AuditActions.PROMOTION_PAUSED, "promotion", promotion.getId().toString(), promotion.getCode(), "SUCCESS");
        return mapper.toCouponResponse(promotion);
    }

    /** Legacy discountType strings map onto the spec-002 types; anything else is a 400 (the legacy table took any text). */
    static PromotionType couponType(String discountType) {
        String normalized = discountType == null ? "" : discountType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PERCENTAGE", "PERCENT", "PCT" -> PromotionType.PERCENTAGE;
            case "FIXED_AMOUNT", "FIXED", "AMOUNT", "VALUE" -> PromotionType.FIXED_AMOUNT;
            default -> throw badRequest("invalid_discount_type", "discountType must be PERCENTAGE or FIXED_AMOUNT");
        };
    }

    /** Display name of a coupon promotion, as V073 names migrated coupons. */
    static String couponName(String code) {
        return "Cupão " + code;
    }

    private void validate(UUID tenantId, CreateCouponRequest request, PromotionType type) {
        if (type == PromotionType.PERCENTAGE && request.discountValue().compareTo(ONE_HUNDRED) > 0) {
            throw badRequest("invalid_percentage_value", "Percentage value must be greater than 0 and at most 100");
        }
        Instant endsAt = request.validUntil() == null ? MarketingMapper.OPEN_ENDED_UNTIL : request.validUntil();
        if (!request.validFrom().isBefore(endsAt)) {
            throw badRequest("invalid_validity_window", "validFrom must be before validUntil");
        }
        if (request.minOrderAmount() != null && request.minOrderAmount().signum() < 0) {
            throw badRequest("invalid_min_order_total", "Minimum order amount cannot be negative");
        }
        if (request.vendorId() != null && vendorRepository.findByTenantIdAndId(tenantId, request.vendorId()).isEmpty()) {
            throw badRequest("vendor_not_found", "Vendor does not belong to this tenant");
        }
    }

    private static BusinessException badRequest(String code, String message) {
        return new BusinessException(code, message, HttpStatus.BAD_REQUEST);
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
