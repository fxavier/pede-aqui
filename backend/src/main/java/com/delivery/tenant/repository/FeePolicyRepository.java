package com.delivery.tenant.repository;

import com.delivery.tenant.entity.FeePolicy;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides tenant-scoped persistence access for fee policy configuration. */
public interface FeePolicyRepository extends JpaRepository<FeePolicy, UUID> {
    Optional<FeePolicy> findByTenantId(UUID tenantId);
    List<FeePolicy> findByTenantIdIn(Collection<UUID> tenantIds);
}
