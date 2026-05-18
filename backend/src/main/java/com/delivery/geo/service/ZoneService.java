package com.delivery.geo.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.geo.dto.CreateZoneRequest;
import com.delivery.geo.dto.ZoneResponse;
import com.delivery.geo.entity.Zone;
import com.delivery.geo.repository.ZoneRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages tenant operations zones used by dispatch and policy configuration. */
@Service
public class ZoneService {
    private final ZoneRepository zoneRepository;
    private final TenantContext tenantContext;

    public ZoneService(ZoneRepository zoneRepository, TenantContext tenantContext) {
        this.zoneRepository = zoneRepository;
        this.tenantContext = tenantContext;
    }

    /** Creates a tenant zone for dispatch and operations workflows. */
    @Transactional
    public ZoneResponse create(CreateZoneRequest request) {
        Zone zone = zoneRepository.save(new Zone(UUID.randomUUID(), tenantId(), request.name(), request.geometryWkt()));
        return toResponse(zone);
    }

    /** Lists all zones scoped to current tenant. */
    @Transactional(readOnly = true)
    public List<ZoneResponse> list() {
        return zoneRepository.findByTenantId(tenantId()).stream().map(this::toResponse).toList();
    }

    private ZoneResponse toResponse(Zone zone) { return new ZoneResponse(zone.getId(), zone.getName(), zone.getStatus(), zone.getGeometryWkt()); }

    private UUID tenantId() {
        return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
