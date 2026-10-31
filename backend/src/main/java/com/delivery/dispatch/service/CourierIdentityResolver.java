package com.delivery.dispatch.service;

import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.security.TenantContext;
import com.delivery.delivery.api.AssignedCourierResolver;
import com.delivery.delivery.api.CourierPosition;
import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.repository.CourierLocationRepository;
import com.delivery.dispatch.repository.CourierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves the caller's courier record (Keycloak user -> app user profile -> courier) for delivery's access guard.
 * Dispatch owns couriers, so it implements the delivery API hook (ADR-0002 C7).
 */
@Component
public class CourierIdentityResolver implements AssignedCourierResolver {
    private final AppUserProfileRepository userProfileRepository;
    private final CourierRepository courierRepository;
    private final TenantContext tenantContext;
    private CourierLocationRepository locationRepository;

    public CourierIdentityResolver(AppUserProfileRepository userProfileRepository, CourierRepository courierRepository,
                                   TenantContext tenantContext) {
        this.userProfileRepository = userProfileRepository;
        this.courierRepository = courierRepository;
        this.tenantContext = tenantContext;
    }

    /** Courier id of the authenticated user in the tenant, if they have a profile and a courier record there. */
    @Override
    public Optional<UUID> currentCourierId(UUID tenantId) {
        return tenantContext.currentKeycloakUserId()
                .flatMap(userId -> userProfileRepository.findByTenantIdAndKeycloakUserId(tenantId, userId))
                .flatMap(profile -> courierRepository.findByTenantIdAndUserProfileId(tenantId, profile.getId()))
                .map(Courier::getId);
    }

    /** LAC-44 arrival evidence: the courier's latest stored position (optional dependency, absent in slice tests). */
    @Autowired(required = false)
    public void setLocationRepository(CourierLocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    /** The courier's latest stored position (W5-B), confined to couriers of the tenant; never logged. */
    @Override
    public Optional<CourierPosition> lastKnownPosition(UUID tenantId, UUID courierId) {
        if (locationRepository == null || courierRepository.findByTenantIdAndId(tenantId, courierId).isEmpty()) {
            return Optional.empty();
        }
        return locationRepository.findLast(courierId)
                .map(point -> new CourierPosition(point.latitude(), point.longitude(), point.accuracyMeters(), point.recordedAt()));
    }
}
