package com.delivery.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.marketing.dto.CouponResponse;
import com.delivery.marketing.dto.CreateCouponRequest;
import com.delivery.marketing.entity.Promotion;
import com.delivery.marketing.entity.PromotionScope;
import com.delivery.marketing.entity.PromotionStatus;
import com.delivery.marketing.entity.PromotionType;
import com.delivery.marketing.mapper.MarketingMapper;
import com.delivery.marketing.repository.PromotionRepository;
import com.delivery.marketing.service.MarketingService;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.repository.VendorRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

/** LAC-29 — legacy coupon request/response ↔ spec-002 promotion mapping of the /marketing/coupons façade. */
class CouponFacadeServiceTest {
    private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant UNTIL = Instant.parse("2026-12-31T23:59:59Z");
    private final UUID tenantId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();
    private final PromotionRepository promotionRepository = mock(PromotionRepository.class);
    private final VendorRepository vendorRepository = mock(VendorRepository.class);
    private final TenantContext tenantContext = mock(TenantContext.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final MarketingService service = new MarketingService(promotionRepository, vendorRepository, new MarketingMapper(),
            tenantContext, auditLogService);

    @BeforeEach
    void setUp() {
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(promotionRepository.save(any(Promotion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vendorRepository.findByTenantIdAndId(tenantId, vendorId)).thenReturn(Optional.of(mock(Vendor.class)));
    }

    @Test
    void createMapsTheCouponOntoAnActiveOrderScopedPromotion() {
        CouponResponse response = service.createCoupon(new CreateCouponRequest(" summer10 ", "percentage",
                new BigDecimal("10"), new BigDecimal("50.00"), 100, vendorId, FROM, UNTIL));

        ArgumentCaptor<Promotion> saved = ArgumentCaptor.forClass(Promotion.class);
        verify(promotionRepository).save(saved.capture());
        Promotion p = saved.getValue();
        assertThat(p.getTenantId()).isEqualTo(tenantId);
        assertThat(p.getCode()).isEqualTo("SUMMER10");
        assertThat(p.getName()).isEqualTo("Cupão SUMMER10");
        assertThat(p.getType()).isEqualTo(PromotionType.PERCENTAGE);
        assertThat(p.getValue()).isEqualByComparingTo("10");
        assertThat(p.getScope()).isEqualTo(PromotionScope.ORDER);
        assertThat(p.getMinOrderTotal()).isEqualByComparingTo("50.00");
        assertThat(p.getMaxDiscountAmount()).isNull();
        assertThat(p.getUsageLimit()).isEqualTo(100);
        assertThat(p.getPerCustomerLimit()).isNull();
        assertThat(p.getVendorId()).isEqualTo(vendorId);
        assertThat(p.getStartsAt()).isEqualTo(FROM);
        assertThat(p.getEndsAt()).isEqualTo(UNTIL);
        assertThat(p.getStatus()).isEqualTo(PromotionStatus.ACTIVE);

        assertThat(response.id()).isEqualTo(p.getId());
        assertThat(response.code()).isEqualTo("SUMMER10");
        assertThat(response.discountType()).isEqualTo("PERCENTAGE");
        assertThat(response.minOrderAmount()).isEqualByComparingTo("50.00");
        assertThat(response.maxUses()).isEqualTo(100);
        assertThat(response.usesCount()).isZero();
        assertThat(response.validUntil()).isEqualTo(UNTIL);
        assertThat(response.active()).isTrue();
        verify(auditLogService).log(eq("PROMOTION_CREATED"), eq("promotion"), eq(p.getId().toString()), eq("SUMMER10"), eq("SUCCESS"));
    }

    @Test
    void openEndedCouponIsStoredWithTheSentinelAndReturnedAsNull() {
        CouponResponse response = service.createCoupon(new CreateCouponRequest("OPEN", "FIXED", new BigDecimal("20"),
                null, null, null, FROM, null));

        ArgumentCaptor<Promotion> saved = ArgumentCaptor.forClass(Promotion.class);
        verify(promotionRepository).save(saved.capture());
        assertThat(saved.getValue().getEndsAt()).isEqualTo(MarketingMapper.OPEN_ENDED_UNTIL);
        assertThat(saved.getValue().getType()).isEqualTo(PromotionType.FIXED_AMOUNT);
        assertThat(response.validUntil()).isNull();
        assertThat(response.discountType()).isEqualTo("FIXED_AMOUNT");
    }

    @Test
    void invalidCouponsAreRejectedBeforeAnyWrite() {
        assertCode(new CreateCouponRequest("X", "BOGO", BigDecimal.ONE, null, null, null, FROM, null), "invalid_discount_type", HttpStatus.BAD_REQUEST);
        assertCode(new CreateCouponRequest("X", "PERCENTAGE", new BigDecimal("101"), null, null, null, FROM, null), "invalid_percentage_value", HttpStatus.BAD_REQUEST);
        assertCode(new CreateCouponRequest("X", "PERCENTAGE", BigDecimal.TEN, null, null, null, UNTIL, FROM), "invalid_validity_window", HttpStatus.BAD_REQUEST);
        assertCode(new CreateCouponRequest("X", "PERCENTAGE", BigDecimal.TEN, new BigDecimal("-1"), null, null, FROM, null), "invalid_min_order_total", HttpStatus.BAD_REQUEST);
        assertCode(new CreateCouponRequest("X", "PERCENTAGE", BigDecimal.TEN, null, null, UUID.randomUUID(), FROM, null), "vendor_not_found", HttpStatus.BAD_REQUEST);
        assertCode(new CreateCouponRequest("A".repeat(41), "PERCENTAGE", BigDecimal.TEN, null, null, null, FROM, null), "invalid_coupon_code", HttpStatus.BAD_REQUEST);
        when(promotionRepository.existsByTenantIdAndCode(tenantId, "TAKEN")).thenReturn(true);
        assertCode(new CreateCouponRequest("taken", "PERCENTAGE", BigDecimal.TEN, null, null, null, FROM, null), "duplicate_coupon_code", HttpStatus.CONFLICT);
        verify(promotionRepository, never()).save(any());
    }

    @Test
    void deactivatePausesAnActiveCouponOnceAndIgnoresAutomaticPromotions() {
        Promotion coupon = promotion("CODE1");
        coupon.activate();
        when(promotionRepository.findByTenantIdAndId(tenantId, coupon.getId())).thenReturn(Optional.of(coupon));

        CouponResponse response = service.deactivateCoupon(coupon.getId());

        assertThat(coupon.getStatus()).isEqualTo(PromotionStatus.PAUSED);
        assertThat(response.active()).isFalse();
        assertCode(() -> service.deactivateCoupon(coupon.getId()), "already_inactive", HttpStatus.CONFLICT);

        Promotion automatic = promotion(null);
        automatic.activate();
        when(promotionRepository.findByTenantIdAndId(tenantId, automatic.getId())).thenReturn(Optional.of(automatic));
        assertCode(() -> service.deactivateCoupon(automatic.getId()), "not_found", HttpStatus.NOT_FOUND);
        assertThat(automatic.getStatus()).isEqualTo(PromotionStatus.ACTIVE);
    }

    private Promotion promotion(String code) {
        return new Promotion(UUID.randomUUID(), tenantId, vendorId, "P", code, PromotionType.PERCENTAGE, BigDecimal.TEN,
                PromotionScope.ORDER, null, null, null, null, FROM, UNTIL, null, null);
    }

    private void assertCode(CreateCouponRequest request, String code, HttpStatus status) {
        assertCode(() -> service.createCoupon(request), code, status);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code, HttpStatus status) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class, e -> {
            assertThat(e.getCode()).isEqualTo(code);
            assertThat(e.getStatus()).isEqualTo(status);
        });
    }
}
