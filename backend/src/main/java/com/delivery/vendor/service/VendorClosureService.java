package com.delivery.vendor.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditActions;
import com.delivery.common.service.AuditLogService;
import com.delivery.vendor.api.MerchantScopeGuard;
import com.delivery.vendor.dto.CreateVendorClosureRequest;
import com.delivery.vendor.dto.VendorClosureResponse;
import com.delivery.vendor.entity.VendorClosure;
import com.delivery.vendor.repository.VendorClosureRepository;
import com.delivery.vendor.repository.VendorRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages exceptional store closures (LAC-27). OPS/ADMIN act on any vendor of the tenant; a VENDOR_ADMIN only on its
 * own vendor (MerchantScopeGuard, 403 vendor_access_denied otherwise). Creations and deletions are audited.
 */
@Service
public class VendorClosureService {
    public static final String TARGET_TYPE = "VENDOR";

    private final VendorClosureRepository closureRepository;
    private final VendorRepository vendorRepository;
    private final MerchantScopeGuard merchantScopeGuard;
    private final TenantContext tenantContext;
    private final AuditLogService auditLogService;

    public VendorClosureService(VendorClosureRepository closureRepository, VendorRepository vendorRepository,
                                MerchantScopeGuard merchantScopeGuard, TenantContext tenantContext, AuditLogService auditLogService) {
        this.closureRepository = closureRepository;
        this.vendorRepository = vendorRepository;
        this.merchantScopeGuard = merchantScopeGuard;
        this.tenantContext = tenantContext;
        this.auditLogService = auditLogService;
    }

    /** Lists the vendor's current and future closures (ended ones are omitted), earliest first. */
    @Transactional(readOnly = true)
    public List<VendorClosureResponse> list(UUID vendorId) {
        UUID tenantId = authorize(vendorId);
        return closureRepository.findByTenantIdAndVendorIdAndEndsAtAfterOrderByStartsAtAsc(tenantId, vendorId, Instant.now())
                .stream().map(VendorClosureResponse::of).toList();
    }

    /** Creates a closure; the period must be non-empty and must not already be over. */
    @Transactional
    public VendorClosureResponse create(UUID vendorId, CreateVendorClosureRequest request) {
        UUID tenantId = authorize(vendorId);
        if (!request.endsAt().isAfter(request.startsAt()) || !request.endsAt().isAfter(Instant.now())) {
            throw new BusinessException("invalid_closure_period", "The closure must end after it starts and after now", HttpStatus.BAD_REQUEST);
        }
        String actor = tenantContext.currentKeycloakUserId().orElse(null);
        VendorClosure closure = closureRepository.save(new VendorClosure(UUID.randomUUID(), tenantId, vendorId,
                request.startsAt(), request.endsAt(), request.reason().trim(), actor));
        auditLogService.log(AuditActions.VENDOR_CLOSURE_CREATED, TARGET_TYPE, vendorId.toString(), closure.getId().toString(), "SUCCESS");
        return VendorClosureResponse.of(closure);
    }

    /** Deletes a closure of the vendor (404 when it does not exist for that vendor in the tenant). */
    @Transactional
    public void delete(UUID vendorId, UUID closureId) {
        UUID tenantId = authorize(vendorId);
        VendorClosure closure = closureRepository.findByTenantIdAndVendorIdAndId(tenantId, vendorId, closureId)
                .orElseThrow(() -> new NotFoundException("Closure was not found"));
        closureRepository.delete(closure);
        auditLogService.log(AuditActions.VENDOR_CLOSURE_DELETED, TARGET_TYPE, vendorId.toString(), closureId.toString(), "SUCCESS");
    }

    /** Resolves the tenant, checks the vendor exists in it and confines merchant users to their own vendor. */
    private UUID authorize(UUID vendorId) {
        UUID tenantId = tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
        vendorRepository.findByTenantIdAndId(tenantId, vendorId).orElseThrow(() -> new NotFoundException("Vendor was not found"));
        if (merchantScopeGuard.isMerchantScoped()) {
            merchantScopeGuard.ensureCallerOwnsVendor(vendorId, "Closures can only be managed for your own vendor");
        }
        return tenantId;
    }
}
