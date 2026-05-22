package com.delivery.finance.repository;

import com.delivery.finance.entity.Commission;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides commission lookups for finance dashboards and exports. */
public interface CommissionRepository extends JpaRepository<Commission, UUID> {
    List<Commission> findByTenantId(UUID tenantId);
    List<Commission> findByTenantIdAndStatus(UUID tenantId, String status);
    List<Commission> findByTenantIdAndVendorId(UUID tenantId, UUID vendorId);
    List<Commission> findByTenantIdAndCreatedAtBetween(UUID tenantId, Instant from, Instant to);
}
