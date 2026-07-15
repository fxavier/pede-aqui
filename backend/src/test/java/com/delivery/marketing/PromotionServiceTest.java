package com.delivery.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.catalog.entity.Category;
import com.delivery.catalog.repository.CategoryRepository;
import com.delivery.catalog.repository.ProductRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.marketing.dto.PromotionResponse;
import com.delivery.marketing.dto.PromotionUpsertRequest;
import com.delivery.marketing.entity.Promotion;
import com.delivery.marketing.entity.PromotionScope;
import com.delivery.marketing.entity.PromotionStatus;
import com.delivery.marketing.entity.PromotionType;
import com.delivery.marketing.mapper.MarketingMapper;
import com.delivery.marketing.repository.PromotionRepository;
import com.delivery.marketing.service.PromotionService;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.repository.VendorRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/** D2 — promotion CRUD validation matrix, vendor-scope guard, lifecycle transitions, and audit. */
class PromotionServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();

    private final PromotionRepository promotionRepository = mock(PromotionRepository.class);
    private final VendorRepository vendorRepository = mock(VendorRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final TenantContext tenantContext = mock(TenantContext.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final PromotionService service = new PromotionService(promotionRepository, vendorRepository,
            categoryRepository, productRepository, new MarketingMapper(), tenantContext, auditLogService);

    @BeforeEach
    void setUp() {
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(promotionRepository.save(any(Promotion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vendorRepository.findByTenantIdAndId(tenantId, vendorId)).thenReturn(Optional.of(mock(Vendor.class)));
        loginWithRoles("ROLE_OPS");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginWithRoles(String... roles) {
        TestingAuthenticationToken auth =
                new TestingAuthenticationToken("user", "n/a", AuthorityUtils.createAuthorityList(roles));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private PromotionUpsertRequest request(UUID vendorId, String code, PromotionType type, BigDecimal value,
                                           PromotionScope scope, UUID targetCategoryId, UUID targetProductId,
                                           Instant startsAt, Instant endsAt) {
        return new PromotionUpsertRequest(vendorId, "Promo", code, type, value, scope, targetCategoryId,
                targetProductId, null, null, startsAt, endsAt, null, null);
    }

    private PromotionUpsertRequest validOrderRequest(UUID vendorId) {
        return request(vendorId, "SAVE10", PromotionType.PERCENTAGE, new BigDecimal("10"), PromotionScope.ORDER,
                null, null, Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.DAYS));
    }

    @Test
    void createsDraftPromotionWithNormalizedCodeAndAudit() {
        PromotionResponse response = service.create(request(vendorId, "  save10 ", PromotionType.PERCENTAGE,
                new BigDecimal("10"), PromotionScope.ORDER, null, null,
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.DAYS)));

        assertThat(response.status()).isEqualTo(PromotionStatus.DRAFT);
        assertThat(response.code()).isEqualTo("SAVE10");
        verify(auditLogService).log(eq("PROMOTION_CREATED"), eq("promotion"), any(), eq("Promo"), eq("SUCCESS"));
    }

    @Test
    void tenantWidePromotionRequiresOpsOrAdmin() {
        loginWithRoles("ROLE_VENDOR_ADMIN");

        assertThatThrownBy(() -> service.create(validOrderRequest(null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
    }

    @Test
    void vendorAdminMayCreateVendorScopedPromotion() {
        loginWithRoles("ROLE_VENDOR_ADMIN");

        assertThat(service.create(validOrderRequest(vendorId)).vendorId()).isEqualTo(vendorId);
    }

    @Test
    void rejectsInvertedValidityWindow() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> service.create(request(vendorId, null, PromotionType.PERCENTAGE, new BigDecimal("10"),
                PromotionScope.ORDER, null, null, now.plus(2, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "invalid_validity_window");
    }

    @Test
    void rejectsPercentageOutOfRange() {
        assertThatThrownBy(() -> service.create(request(vendorId, null, PromotionType.PERCENTAGE, new BigDecimal("101"),
                PromotionScope.ORDER, null, null, Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "invalid_percentage_value");
    }

    @Test
    void rejectsNonPositiveFixedAmount() {
        assertThatThrownBy(() -> service.create(request(vendorId, null, PromotionType.FIXED_AMOUNT, BigDecimal.ZERO,
                PromotionScope.ORDER, null, null, Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "invalid_fixed_amount_value");
    }

    @Test
    void categoryScopeRequiresTenantCategory() {
        when(categoryRepository.findByTenantIdAndId(tenantId, categoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(vendorId, null, PromotionType.PERCENTAGE, new BigDecimal("10"),
                PromotionScope.CATEGORY, categoryId, null, Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "target_category_not_found");
    }

    @Test
    void orderScopeRejectsStrayTargets() {
        when(categoryRepository.findByTenantIdAndId(tenantId, categoryId)).thenReturn(Optional.of(mock(Category.class)));

        assertThatThrownBy(() -> service.create(request(vendorId, null, PromotionType.PERCENTAGE, new BigDecimal("10"),
                PromotionScope.ORDER, categoryId, null, Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "invalid_scope_target");
    }

    @Test
    void duplicateCouponCodeConflicts() {
        when(promotionRepository.existsByTenantIdAndCode(tenantId, "SAVE10")).thenReturn(true);

        assertThatThrownBy(() -> service.create(validOrderRequest(vendorId)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "duplicate_promotion_code");
    }

    @Test
    void expiredPromotionCannotBeActivated() {
        Promotion expired = new Promotion(UUID.randomUUID(), tenantId, vendorId, "Old", null,
                PromotionType.PERCENTAGE, new BigDecimal("10"), PromotionScope.ORDER, null, null, null, null,
                Instant.now().minus(10, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.DAYS), null, null);
        when(promotionRepository.findByTenantIdAndId(tenantId, expired.getId())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.activate(expired.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "promotion_expired");
        assertThat(expired.getStatus()).isEqualTo(PromotionStatus.EXPIRED);
    }

    @Test
    void activePromotionCannotBeDeleted() {
        Promotion active = new Promotion(UUID.randomUUID(), tenantId, vendorId, "Live", null,
                PromotionType.PERCENTAGE, new BigDecimal("10"), PromotionScope.ORDER, null, null, null, null,
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.DAYS), null, null);
        active.activate();
        when(promotionRepository.findByTenantIdAndId(tenantId, active.getId())).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.delete(active.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
    }

    @Test
    void draftPromotionActivatesAndPausesWithAudit() {
        Promotion draft = new Promotion(UUID.randomUUID(), tenantId, vendorId, "New", null,
                PromotionType.PERCENTAGE, new BigDecimal("10"), PromotionScope.ORDER, null, null, null, null,
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.DAYS), null, null);
        when(promotionRepository.findByTenantIdAndId(tenantId, draft.getId())).thenReturn(Optional.of(draft));

        assertThat(service.activate(draft.getId()).status()).isEqualTo(PromotionStatus.ACTIVE);
        verify(auditLogService).log(eq("PROMOTION_ACTIVATED"), eq("promotion"), any(), eq("New"), eq("SUCCESS"));

        assertThat(service.pause(draft.getId()).status()).isEqualTo(PromotionStatus.PAUSED);
        verify(auditLogService).log(eq("PROMOTION_PAUSED"), eq("promotion"), any(), eq("New"), eq("SUCCESS"));
    }

    @Test
    void vendorAdminCannotSeeTenantWidePromotionsInList() {
        loginWithRoles("ROLE_VENDOR_ADMIN");
        Promotion tenantWide = new Promotion(UUID.randomUUID(), tenantId, null, "Wide", null,
                PromotionType.PERCENTAGE, new BigDecimal("10"), PromotionScope.ORDER, null, null, null, null,
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.DAYS), null, null);
        Promotion scoped = new Promotion(UUID.randomUUID(), tenantId, vendorId, "Mine", null,
                PromotionType.PERCENTAGE, new BigDecimal("10"), PromotionScope.ORDER, null, null, null, null,
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.DAYS), null, null);
        when(promotionRepository.findByTenantId(tenantId)).thenReturn(java.util.List.of(tenantWide, scoped));

        assertThat(service.list(null, null)).extracting(PromotionResponse::name).containsExactly("Mine");
    }
}
