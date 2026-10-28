package com.delivery.dispatch.repository;

import com.delivery.dispatch.entity.DispatchJob;
import com.delivery.dispatch.entity.DispatchJobStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Provides tenant-scoped dispatch job persistence queries. */
public interface DispatchJobRepository extends JpaRepository<DispatchJob, UUID> {
    Optional<DispatchJob> findByTenantIdAndId(UUID tenantId, UUID id);
    List<DispatchJob> findByTenantIdAndCourierId(UUID tenantId, UUID courierId);
    List<DispatchJob> findByTenantIdAndStatus(UUID tenantId, DispatchJobStatus status);
    long countByTenantIdAndStatus(UUID tenantId, DispatchJobStatus status);
    long countByTenantIdAndCourierIdAndStatus(UUID tenantId, UUID courierId, DispatchJobStatus status);

    /** One page of the tenant's jobs (ordering comes from the Pageable). */
    List<DispatchJob> findByTenantId(UUID tenantId, Pageable pageable);

    /** One page of a single courier's jobs within the tenant. */
    List<DispatchJob> findByTenantIdAndCourierId(UUID tenantId, UUID courierId, Pageable pageable);

    /** The delivery's jobs in the given statuses (used to cancel its active job when the delivery is retired). */
    List<DispatchJob> findByTenantIdAndDeliveryIdAndStatusIn(UUID tenantId, UUID deliveryId, Collection<DispatchJobStatus> statuses);

    /** True when the delivery already has a job in one of the given (active) statuses. */
    boolean existsByTenantIdAndDeliveryIdAndStatusIn(UUID tenantId, UUID deliveryId, Collection<DispatchJobStatus> statuses);

    /**
     * Atomically accepts an offer: succeeds (returns 1) only for the addressed courier, while the job is
     * still ASSIGNED and its offer has not expired. Concurrent callers serialize on the row lock, so at
     * most one of them sees 1; everyone else gets 0. Clears the persistence context afterwards.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE DispatchJob j
               SET j.status = :accepted, j.acceptedAt = :now, j.updatedAt = :now, j.version = j.version + 1
             WHERE j.id = :id AND j.tenantId = :tenantId AND j.courierId = :courierId
               AND j.status = :assigned
               AND (j.expiresAt IS NULL OR j.expiresAt > :now)""")
    int acceptOffer(@Param("id") UUID id,
                    @Param("tenantId") UUID tenantId,
                    @Param("courierId") UUID courierId,
                    @Param("now") Instant now,
                    @Param("assigned") DispatchJobStatus assigned,
                    @Param("accepted") DispatchJobStatus accepted);
}
