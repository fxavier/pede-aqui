package com.delivery.dispatch.repository;

import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.entity.CourierStatus;
import com.delivery.dispatch.entity.CourierVerificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Provides courier queries for dispatch eligibility, presence sweeping and profile updates. */
public interface CourierRepository extends JpaRepository<Courier, UUID> {
    Optional<Courier> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<Courier> findByTenantIdAndUserProfileId(UUID tenantId, UUID userProfileId);
    List<Courier> findByTenantId(UUID tenantId);
    Page<Courier> findByTenantId(UUID tenantId, Pageable pageable);
    /** Dispatch eligibility (LAC-42): verified couriers of the zone in the given operational status. */
    List<Courier> findByTenantIdAndVerificationStatusAndStatusAndOperatingZoneId(UUID tenantId, CourierVerificationStatus verificationStatus,
                                                                                 CourierStatus status, UUID operatingZoneId);
    long countByTenantIdAndAvailable(UUID tenantId, boolean available);

    /**
     * Presence sweeper candidates (read-only, bounded, oldest first): AVAILABLE couriers that went online before the
     * cutoff and whose last location (V061 courier_last_locations.received_at) is missing or older than the cutoff.
     */
    @Query(value = """
            SELECT c.id FROM couriers c
             WHERE c.status = 'AVAILABLE'
               AND c.status_changed_at < :cutoff
               AND NOT EXISTS (SELECT 1 FROM courier_last_locations l WHERE l.courier_id = c.id AND l.received_at >= :cutoff)
             ORDER BY c.status_changed_at
             LIMIT :limit""", nativeQuery = true)
    List<UUID> findStaleAvailableIds(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    /** Self-heal candidates: BUSY couriers without any delivery in a courier-held status (bounded). */
    @Query(value = """
            SELECT c.id FROM couriers c
             WHERE c.status = 'BUSY'
               AND NOT EXISTS (SELECT 1 FROM deliveries d WHERE d.courier_id = c.id
                                AND d.status IN ('ACCEPTED', 'ARRIVED_AT_VENDOR', 'PICKED_UP', 'ON_ROUTE_TO_CUSTOMER', 'ARRIVED_AT_CUSTOMER'))
             ORDER BY c.status_changed_at
             LIMIT :limit""", nativeQuery = true)
    List<UUID> findIdleBusyIds(@Param("limit") int limit);

    /** Claims one courier row for a sweeper item if it is still in the expected status; empty when held or changed. */
    @Query(value = "SELECT c.id FROM couriers c WHERE c.id = :id AND c.status = :status FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<UUID> lockIdInStatusSkipLocked(@Param("id") UUID id, @Param("status") String status);
}
