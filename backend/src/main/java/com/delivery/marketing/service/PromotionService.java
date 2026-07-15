package com.delivery.marketing.service;

import com.delivery.catalog.repository.CategoryRepository;
import com.delivery.catalog.repository.ProductRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditActions;
import com.delivery.common.service.AuditLogService;
import com.delivery.marketing.dto.PromotionResponse;
import com.delivery.marketing.dto.PromotionUpsertRequest;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages spec-002 promotion CRUD and lifecycle with tenant/vendor guards, coherence validation, and audit. */
@Service
public class PromotionService {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private final PromotionRepository promotionRepository;
    private final VendorRepository vendorRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final MarketingMapper mapper;
    private final TenantContext tenantContext;
    private final AuditLogService auditLogService;

    public PromotionService(PromotionRepository promotionRepository, VendorRepository vendorRepository,
                            CategoryRepository categoryRepository, ProductRepository productRepository,
                            MarketingMapper mapper, TenantContext tenantContext, AuditLogService auditLogService) {
        this.promotionRepository = promotionRepository;
        this.vendorRepository = vendorRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
        this.auditLogService = auditLogService;
    }

    /** Lists tenant promotions; VENDOR_ADMIN sees only vendor-scoped ones, OPS/ADMIN see all incl. tenant-wide. */
    @Transactional(readOnly = true)
    public List<PromotionResponse> list(PromotionStatus status, UUID vendorId) {
        Instant now = Instant.now();
        boolean tenantWideVisible = hasTenantWideRole();
        return promotionRepository.findByTenantId(tenantId()).stream()
                .filter(p -> tenantWideVisible || p.getVendorId() != null)
                .filter(p -> status == null || p.effectiveStatus(now) == status)
                .filter(p -> vendorId == null || vendorId.equals(p.getVendorId()))
                .map(mapper::toPromotionResponse)
                .toList();
    }

    /** Creates a DRAFT promotion after full validity/coherence validation (AC-7.1, AC-7.2, AC-7.4). */
    @Transactional
    public PromotionResponse create(PromotionUpsertRequest request) {
        UUID tenantId = tenantId();
        String code = normalizeCode(request.code());
        validate(tenantId, request);
        if (code != null && promotionRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new BusinessException("duplicate_promotion_code", "A promotion with this code already exists", HttpStatus.CONFLICT);
        }
        Promotion promotion = new Promotion(UUID.randomUUID(), tenantId, request.vendorId(), request.name(), code,
                request.type(), request.value(), request.scope(), request.targetCategoryId(), request.targetProductId(),
                request.minOrderTotal(), request.maxDiscountAmount(), request.startsAt(), request.endsAt(),
                request.usageLimit(), request.perCustomerLimit());
        Promotion saved = promotionRepository.save(promotion);
        auditLogService.log(AuditActions.PROMOTION_CREATED, "promotion", saved.getId().toString(), saved.getName(), "SUCCESS");
        return mapper.toPromotionResponse(saved);
    }

    /** Updates a non-expired promotion with full re-validation of the new state. */
    @Transactional
    public PromotionResponse update(UUID id, PromotionUpsertRequest request) {
        UUID tenantId = tenantId();
        Promotion promotion = findGuarded(tenantId, id);
        if (promotion.effectiveStatus(Instant.now()) == PromotionStatus.EXPIRED) {
            throw new BusinessException("promotion_expired", "An expired promotion cannot be updated", HttpStatus.CONFLICT);
        }
        String code = normalizeCode(request.code());
        validate(tenantId, request);
        if (code != null && !code.equals(promotion.getCode()) && promotionRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new BusinessException("duplicate_promotion_code", "A promotion with this code already exists", HttpStatus.CONFLICT);
        }
        promotion.applyDetails(request.vendorId(), request.name(), code, request.type(), request.value(),
                request.scope(), request.targetCategoryId(), request.targetProductId(), request.minOrderTotal(),
                request.maxDiscountAmount(), request.startsAt(), request.endsAt(), request.usageLimit(),
                request.perCustomerLimit());
        auditLogService.log(AuditActions.PROMOTION_UPDATED, "promotion", promotion.getId().toString(), promotion.getName(), "SUCCESS");
        return mapper.toPromotionResponse(promotion);
    }

    /** Activates a DRAFT/PAUSED promotion; expired promotions resolve EXPIRED and cannot activate (AC-7.3). */
    @Transactional
    public PromotionResponse activate(UUID id) {
        Promotion promotion = findGuarded(tenantId(), id);
        if (promotion.isExpired(Instant.now())) {
            promotion.markExpired();
            throw new BusinessException("promotion_expired", "An expired promotion cannot be activated", HttpStatus.CONFLICT);
        }
        if (promotion.getStatus() != PromotionStatus.ACTIVE) {
            promotion.activate();
        }
        auditLogService.log(AuditActions.PROMOTION_ACTIVATED, "promotion", promotion.getId().toString(), promotion.getName(), "SUCCESS");
        return mapper.toPromotionResponse(promotion);
    }

    /** Pauses an ACTIVE promotion (AC-7.3). */
    @Transactional
    public PromotionResponse pause(UUID id) {
        Promotion promotion = findGuarded(tenantId(), id);
        PromotionStatus effective = promotion.effectiveStatus(Instant.now());
        if (effective != PromotionStatus.ACTIVE && effective != PromotionStatus.PAUSED) {
            throw new BusinessException("invalid_promotion_transition", "Only an active promotion can be paused", HttpStatus.CONFLICT);
        }
        if (promotion.getStatus() != PromotionStatus.PAUSED) {
            promotion.pause();
        }
        auditLogService.log(AuditActions.PROMOTION_PAUSED, "promotion", promotion.getId().toString(), promotion.getName(), "SUCCESS");
        return mapper.toPromotionResponse(promotion);
    }

    /** Deletes a DRAFT/PAUSED promotion; ACTIVE (or expired) promotions return 409. */
    @Transactional
    public void delete(UUID id) {
        Promotion promotion = findGuarded(tenantId(), id);
        PromotionStatus effective = promotion.effectiveStatus(Instant.now());
        if (effective != PromotionStatus.DRAFT && effective != PromotionStatus.PAUSED) {
            throw new BusinessException("promotion_not_deletable", "Only draft or paused promotions can be deleted", HttpStatus.CONFLICT);
        }
        promotionRepository.delete(promotion);
        auditLogService.log(AuditActions.PROMOTION_DELETED, "promotion", promotion.getId().toString(), promotion.getName(), "SUCCESS");
    }

    /** Loads a tenant promotion and enforces the vendor-scope guard for the current caller. */
    private Promotion findGuarded(UUID tenantId, UUID id) {
        Promotion promotion = promotionRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new NotFoundException("Promotion was not found"));
        requireVendorScopeAllowed(promotion.getVendorId());
        return promotion;
    }

    /** Validates type/value, scope/target, window coherence and vendor scope (AC-7.2, AC-7.4). */
    private void validate(UUID tenantId, PromotionUpsertRequest request) {
        requireVendorScopeAllowed(request.vendorId());
        if (request.startsAt() == null || request.endsAt() == null || !request.startsAt().isBefore(request.endsAt())) {
            throw badRequest("invalid_validity_window", "startsAt must be before endsAt");
        }
        if (request.type() == PromotionType.PERCENTAGE
                && (request.value().signum() <= 0 || request.value().compareTo(ONE_HUNDRED) > 0)) {
            throw badRequest("invalid_percentage_value", "Percentage value must be greater than 0 and at most 100");
        }
        if (request.type() == PromotionType.FIXED_AMOUNT && request.value().signum() <= 0) {
            throw badRequest("invalid_fixed_amount_value", "Fixed amount value must be greater than 0");
        }
        if (request.minOrderTotal() != null && request.minOrderTotal().signum() < 0) {
            throw badRequest("invalid_min_order_total", "Minimum order total cannot be negative");
        }
        if (request.maxDiscountAmount() != null && request.maxDiscountAmount().signum() <= 0) {
            throw badRequest("invalid_max_discount_amount", "Maximum discount amount must be greater than 0");
        }
        validateScopeTargets(tenantId, request);
        if (request.vendorId() != null && vendorRepository.findByTenantIdAndId(tenantId, request.vendorId()).isEmpty()) {
            throw badRequest("vendor_not_found", "Vendor does not belong to this tenant");
        }
    }

    /** Enforces scope/target coherence: target required iff the scope demands it, and in the same tenant. */
    private void validateScopeTargets(UUID tenantId, PromotionUpsertRequest request) {
        PromotionScope scope = request.scope();
        if (scope == PromotionScope.ORDER && (request.targetCategoryId() != null || request.targetProductId() != null)) {
            throw badRequest("invalid_scope_target", "ORDER scope must not carry a category or product target");
        }
        if (scope == PromotionScope.CATEGORY) {
            if (request.targetCategoryId() == null || request.targetProductId() != null) {
                throw badRequest("invalid_scope_target", "CATEGORY scope requires exactly a target category");
            }
            if (categoryRepository.findByTenantIdAndId(tenantId, request.targetCategoryId()).isEmpty()) {
                throw badRequest("target_category_not_found", "Target category does not belong to this tenant");
            }
        }
        if (scope == PromotionScope.PRODUCT) {
            if (request.targetProductId() == null || request.targetCategoryId() != null) {
                throw badRequest("invalid_scope_target", "PRODUCT scope requires exactly a target product");
            }
            if (productRepository.findByTenantIdAndId(tenantId, request.targetProductId()).isEmpty()) {
                throw badRequest("target_product_not_found", "Target product does not belong to this tenant");
            }
        }
    }

    /** Tenant-wide promotions (vendor_id null) are OPS/ADMIN only (AC-7.2). */
    private void requireVendorScopeAllowed(UUID vendorId) {
        if (vendorId == null && !hasTenantWideRole()) {
            throw new BusinessException("tenant_wide_promotion_forbidden",
                    "Tenant-wide promotions require OPS or ADMIN role", HttpStatus.FORBIDDEN);
        }
    }

    private boolean hasTenantWideRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a ->
                "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_OPS".equals(a.getAuthority()));
    }

    private static BusinessException badRequest(String code, String message) {
        return new BusinessException(code, message, HttpStatus.BAD_REQUEST);
    }

    /** Normalises coupon codes to trimmed uppercase so lookups are case-insensitive. */
    static String normalizeCode(String code) {
        if (code == null || code.isBlank()) return null;
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
