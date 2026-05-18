package com.delivery.tenant.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.tenant.dto.CreateTenantRequest;
import com.delivery.tenant.dto.TenantResponse;
import com.delivery.tenant.mapper.TenantMapper;
import com.delivery.tenant.entity.Tenant;
import com.delivery.tenant.repository.TenantRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contains tenant management and validation rules. */
@Service
public class TenantService {
    private final TenantRepository repository;
    private final TenantMapper mapper;

    public TenantService(TenantRepository repository, TenantMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /** Creates a new active tenant with a unique slug. */
    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        if (repository.existsBySlug(request.slug())) {
            throw new BusinessException("tenant_slug_exists", "Tenant slug already exists", HttpStatus.CONFLICT);
        }
        Tenant tenant = new Tenant(UUID.randomUUID(), request.name(), request.slug(), request.defaultCurrency());
        return mapper.toResponse(repository.save(tenant));
    }

    /** Lists tenants for platform administrators. */
    @Transactional(readOnly = true)
    public List<TenantResponse> listTenants() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
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
        tenant.updateStatus(status);
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
