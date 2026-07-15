package com.delivery.marketing.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.marketing.dto.CouponResponse;
import com.delivery.marketing.dto.CreateCouponRequest;
import com.delivery.marketing.entity.Coupon;
import com.delivery.marketing.mapper.MarketingMapper;
import com.delivery.marketing.repository.CouponRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages legacy coupons within the current tenant scope (spec-002 promotions live in PromotionService). */
@Service
public class MarketingService {
    private final CouponRepository couponRepository;
    private final MarketingMapper mapper;
    private final TenantContext tenantContext;

    public MarketingService(CouponRepository couponRepository, MarketingMapper mapper, TenantContext tenantContext) {
        this.couponRepository = couponRepository;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
    }

    @Transactional
    public CouponResponse createCoupon(CreateCouponRequest request) {
        UUID tenantId = tenantId();
        if (couponRepository.existsByTenantIdAndCode(tenantId, request.code().toUpperCase())) {
            throw new BusinessException("duplicate_coupon_code", "A coupon with this code already exists", HttpStatus.CONFLICT);
        }
        Coupon coupon = new Coupon(UUID.randomUUID(), tenantId, request.code(), request.discountType(),
                request.discountValue(), request.minOrderAmount(), request.maxUses(),
                request.vendorId(), request.validFrom(), request.validUntil());
        return mapper.toCouponResponse(couponRepository.save(coupon));
    }

    @Transactional(readOnly = true)
    public List<CouponResponse> listCoupons() {
        return couponRepository.findByTenantId(tenantId()).stream().map(mapper::toCouponResponse).toList();
    }

    @Transactional
    public CouponResponse deactivateCoupon(UUID id) {
        UUID tenantId = tenantId();
        Coupon coupon = couponRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new NotFoundException("Coupon not found"));
        coupon.deactivate();
        return mapper.toCouponResponse(couponRepository.save(coupon));
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
