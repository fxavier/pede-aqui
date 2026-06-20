package com.delivery.catalog.service;

import com.delivery.catalog.dto.CreateVerticalRequest;
import com.delivery.catalog.dto.UpdateVerticalRequest;
import com.delivery.catalog.dto.VerticalResponse;
import com.delivery.catalog.entity.Vertical;
import com.delivery.catalog.repository.VerticalRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CRUD operations for tenant-scoped business verticals. */
@Service
public class VerticalService {
    private final VerticalRepository verticalRepository;
    private final TenantContext tenantContext;

    public VerticalService(VerticalRepository verticalRepository, TenantContext tenantContext) {
        this.verticalRepository = verticalRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional(readOnly = true)
    public List<VerticalResponse> listAll() {
        return verticalRepository.findByTenantId(tenantId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public VerticalResponse create(CreateVerticalRequest request) {
        Vertical vertical = new Vertical(UUID.randomUUID(), tenantId(), request.label());
        if (verticalRepository.existsByTenantIdAndSlugAndIdNot(tenantId(), vertical.getSlug(), vertical.getId())) {
            throw new BusinessException("duplicate_vertical", "A vertical with this name already exists", HttpStatus.CONFLICT);
        }
        verticalRepository.save(vertical);
        return toResponse(vertical);
    }

    @Transactional
    public VerticalResponse update(UUID verticalId, UpdateVerticalRequest request) {
        UUID tenantId = tenantId();
        Vertical vertical = verticalRepository.findByTenantIdAndId(tenantId, verticalId)
                .orElseThrow(() -> new NotFoundException("Vertical not found"));
        vertical.update(request.label(), request.active());
        if (verticalRepository.existsByTenantIdAndSlugAndIdNot(tenantId, vertical.getSlug(), verticalId)) {
            throw new BusinessException("duplicate_vertical", "A vertical with this name already exists", HttpStatus.CONFLICT);
        }
        return toResponse(vertical);
    }

    @Transactional
    public void delete(UUID verticalId) {
        UUID tenantId = tenantId();
        Vertical vertical = verticalRepository.findByTenantIdAndId(tenantId, verticalId)
                .orElseThrow(() -> new NotFoundException("Vertical not found"));
        verticalRepository.delete(vertical);
    }

    private VerticalResponse toResponse(Vertical v) {
        return new VerticalResponse(v.getId(), v.getSlug(), v.getLabel(), v.isActive());
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
