package com.delivery.vendor.repository;

import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.entity.VendorVerificationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides tenant-scoped vendor persistence queries. */
public interface VendorRepository extends JpaRepository<Vendor, UUID> {
    Optional<Vendor> findByTenantIdAndId(UUID tenantId, UUID id);
    List<Vendor> findByTenantIdAndStatus(UUID tenantId, String status);
    List<Vendor> findByTenantIdAndAvailable(UUID tenantId, boolean available);
    List<Vendor> findByTenantIdAndCategoryId(UUID tenantId, UUID categoryId);
    List<Vendor> findByTenantIdAndVerificationStatus(UUID tenantId, VendorVerificationStatus verificationStatus);
    long countByTenantIdAndAvailable(UUID tenantId, boolean available);
}
