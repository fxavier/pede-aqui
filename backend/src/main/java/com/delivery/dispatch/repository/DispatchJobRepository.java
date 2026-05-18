package com.delivery.dispatch.repository;

import com.delivery.dispatch.entity.DispatchJob;
import com.delivery.dispatch.entity.DispatchJobStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides tenant-scoped dispatch job persistence queries. */
public interface DispatchJobRepository extends JpaRepository<DispatchJob, UUID> {
    Optional<DispatchJob> findByTenantIdAndId(UUID tenantId, UUID id);
    List<DispatchJob> findByTenantIdAndCourierId(UUID tenantId, UUID courierId);
    List<DispatchJob> findByTenantIdAndStatus(UUID tenantId, DispatchJobStatus status);
    long countByTenantIdAndStatus(UUID tenantId, DispatchJobStatus status);
}
