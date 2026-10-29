package com.delivery.tenant.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.pagination.CappedList;
import com.delivery.common.pagination.PageLimits;
import com.delivery.common.pagination.PageResponse;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditActions;
import com.delivery.common.service.AuditLogService;
import com.delivery.tenant.dto.CreateTenantRequest;
import com.delivery.tenant.dto.TenantResponse;
import com.delivery.tenant.mapper.TenantMapper;
import com.delivery.tenant.entity.Tenant;
import com.delivery.tenant.repository.TenantRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contains tenant management and validation rules. */
@Service
public class TenantService {
    private final TenantRepository repository;
    private final TenantMapper mapper;
    private final AuditLogService auditLogService;
    private final TenantContext tenantContext;

    public TenantService(TenantRepository repository, TenantMapper mapper, AuditLogService auditLogService, TenantContext tenantContext) {
        this.repository = repository;
        this.mapper = mapper;
        this.auditLogService = auditLogService;
        this.tenantContext = tenantContext;
    }

    /** Creates a new active tenant with a unique slug (audited against the new tenant, LAC-15). */
    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        if (repository.existsBySlug(request.slug())) {
            throw new BusinessException("tenant_slug_exists", "Tenant slug already exists", HttpStatus.CONFLICT);
        }
        Tenant tenant = new Tenant(UUID.randomUUID(), request.name(), request.slug(), request.defaultCurrency());
        // saveAndFlush: the audit row references the tenant through a foreign key.
        Tenant saved = repository.saveAndFlush(tenant);
        auditLogService.logForTenant(saved.getId(), AuditActions.TENANT_CREATED, "tenant", saved.getId().toString(), saved.getSlug(), "SUCCESS", null);
        return mapper.toResponse(saved);
    }

    /** Lists the newest tenants for platform administrators, capped at 200 (legacy array, LAC-14). */
    @Transactional(readOnly = true)
    public CappedList<TenantResponse> listTenants() {
        return CappedList.of(repository.findAll(PageLimits.legacyCap()), mapper::toResponse);
    }

    /**
     * Tenants visible to an ADMIN caller of {@code GET /admin/tenants} (W3-D): the platform super-admin sees every
     * tenant (capped list); a tenant-scoped ADMIN only its own tenant, as a one-element array (empty when the
     * tenant row does not exist). Same array shape for both, so the backoffice needs no branch.
     */
    @Transactional(readOnly = true)
    public CappedList<TenantResponse> listTenantsForCurrentAdmin() {
        if (tenantContext.isPlatformAdmin()) {
            return listTenants();
        }
        Optional<UUID> ownTenantId = tenantContext.currentTenantId();
        List<TenantResponse> own = ownTenantId.flatMap(repository::findById).map(mapper::toResponse).stream().toList();
        return new CappedList<>(own, false);
    }

    /** Lists one offset page of tenants, newest first (size capped at 100). */
    @Transactional(readOnly = true)
    public PageResponse<TenantResponse> listTenantsPage(int page, int size) {
        return PageResponse.of(repository.findAll(PageLimits.page(page, size)), mapper::toResponse);
    }

    /** Gets a tenant by ID or returns a not-found error. */
    @Transactional(readOnly = true)
    public TenantResponse getTenant(UUID id) {
        return mapper.toResponse(findTenant(id));
    }

    /** Updates the tenant status used by service-level tenant validation. */
    @Transactional
    public TenantResponse updateStatus(UUID id, String status) {
        Tenant tenant = findTenant(id);
        String previous = tenant.getStatus();
        tenant.updateStatus(status);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", previous);
        details.put("to", status);
        auditLogService.logForTenant(id, AuditActions.TENANT_STATUS_CHANGED, "tenant", id.toString(), tenant.getSlug(), "SUCCESS", details);
        return mapper.toResponse(tenant);
    }

    /** Ensures a tenant exists and is active before tenant-scoped operations run. */
    @Transactional(readOnly = true)
    public void validateActiveTenant(UUID id) {
        Tenant tenant = findTenant(id);
        if (!"ACTIVE".equals(tenant.getStatus())) {
            throw new BusinessException("tenant_inactive", "Tenant is not active", HttpStatus.FORBIDDEN);
        }
    }

    private Tenant findTenant(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Tenant was not found"));
    }
}
