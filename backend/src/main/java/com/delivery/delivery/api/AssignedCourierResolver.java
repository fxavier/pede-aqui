package com.delivery.delivery.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the courier profile of the authenticated caller in a tenant (by user profile, not by JWT role alone).
 * Declared by delivery for its access guard and implemented by the dispatch module, which owns couriers
 * (ADR-0002 C7: delivery no longer depends on dispatch).
 */
public interface AssignedCourierResolver {

    /** Courier id of the caller in the tenant; empty when the caller has no profile or no courier record. */
    Optional<UUID> currentCourierId(UUID tenantId);

    /**
     * The courier's last received position (W5-B {@code courier_last_locations}), used as arrival evidence when a
     * delivery fails (LAC-44). Empty when none was received or the implementation has no location store.
     */
    default Optional<CourierPosition> lastKnownPosition(UUID tenantId, UUID courierId) {
        return Optional.empty();
    }
}
