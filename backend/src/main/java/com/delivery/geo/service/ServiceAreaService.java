package com.delivery.geo.service;

import com.delivery.common.pricing.GeoPoint;
import com.delivery.geo.api.ServiceArea;
import com.delivery.geo.repository.ZoneRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** PostGIS implementation of the zone service-area check (LAC-39): ST_Covers over zones.area. */
@Service
public class ServiceAreaService implements ServiceArea {
    private final ZoneRepository zoneRepository;

    public ServiceAreaService(ZoneRepository zoneRepository) {
        this.zoneRepository = zoneRepository;
    }

    /** True when the tenant's ACTIVE zone covers the point; a null zone or point is never covered. */
    @Override
    @Transactional(readOnly = true)
    public boolean covers(UUID tenantId, UUID zoneId, GeoPoint point) {
        Objects.requireNonNull(tenantId, "tenantId");
        if (zoneId == null || point == null) {
            return false;
        }
        return zoneRepository.zoneCovers(tenantId, zoneId, point.latitude(), point.longitude());
    }

    /** True when any ACTIVE zone of the tenant covers the point; a null point is never covered. */
    @Override
    @Transactional(readOnly = true)
    public boolean covers(UUID tenantId, GeoPoint point) {
        Objects.requireNonNull(tenantId, "tenantId");
        if (point == null) {
            return false;
        }
        return zoneRepository.anyZoneCovers(tenantId, point.latitude(), point.longitude());
    }
}
