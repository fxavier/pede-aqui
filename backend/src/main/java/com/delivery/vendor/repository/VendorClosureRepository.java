package com.delivery.vendor.repository;

import com.delivery.vendor.entity.VendorClosure;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides persistence access for exceptional vendor closures (LAC-27). */
public interface VendorClosureRepository extends JpaRepository<VendorClosure, UUID> {
    List<VendorClosure> findByVendorIdInAndEndsAtAfter(Collection<UUID> vendorIds, Instant at);
    /** Current and future closures of one vendor; the caller passes a capped Pageable (LAC-14 convention). */
    Page<VendorClosure> findByTenantIdAndVendorIdAndEndsAtAfter(UUID tenantId, UUID vendorId, Instant at, Pageable pageable);
    Optional<VendorClosure> findByTenantIdAndVendorIdAndId(UUID tenantId, UUID vendorId, UUID id);
}
