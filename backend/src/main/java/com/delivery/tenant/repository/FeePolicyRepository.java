package com.delivery.tenant.repository;

import com.delivery.tenant.entity.FeePolicy;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides tenant-scoped persistence access for fee policy configuration. */
public interface FeePolicyRepository extends JpaRepository<FeePolicy, UUID> {
    Optional<FeePolicy> findByTenantId(UUID tenantId);
}
