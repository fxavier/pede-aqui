package com.delivery.vendor.repository;

import com.delivery.vendor.entity.VendorClosure;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides persistence access for exceptional vendor closures (LAC-27). */
public interface VendorClosureRepository extends JpaRepository<VendorClosure, UUID> {
    List<VendorClosure> findByVendorIdInAndEndsAtAfter(Collection<UUID> vendorIds, Instant at);
    List<VendorClosure> findByTenantIdAndVendorIdAndEndsAtAfterOrderByStartsAtAsc(UUID tenantId, UUID vendorId, Instant at);
    Optional<VendorClosure> findByTenantIdAndVendorIdAndId(UUID tenantId, UUID vendorId, UUID id);
}
