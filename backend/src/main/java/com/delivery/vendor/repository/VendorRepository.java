package com.delivery.vendor.repository;

import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.entity.VendorVerificationStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Provides tenant-scoped vendor persistence queries. */
public interface VendorRepository extends JpaRepository<Vendor, UUID> {
    Optional<Vendor> findByTenantIdAndId(UUID tenantId, UUID id);
    List<Vendor> findByTenantIdAndStatus(UUID tenantId, String status);
    List<Vendor> findByTenantIdAndAvailable(UUID tenantId, boolean available);
    List<Vendor> findByTenantId(UUID tenantId);
    Page<Vendor> findByTenantId(UUID tenantId, Pageable pageable);
    Page<Vendor> findByTenantIdAndAvailable(UUID tenantId, boolean available, Pageable pageable);
    List<Vendor> findByTenantIdAndCategoryId(UUID tenantId, UUID categoryId);
    List<Vendor> findByTenantIdAndVerificationStatus(UUID tenantId, VendorVerificationStatus verificationStatus);
    long countByTenantIdAndAvailable(UUID tenantId, boolean available);

    /**
     * Server-side straight-line (geodesic, PostGIS geography) distance in km from each vendor to a point (INC-13),
     * computed on {@code vendors.location} (LAC-39). Vendors without a location (coordinates missing, (0,0)
     * placeholder or out of range — see V062's sync trigger) are not returned.
     */
    @Query(value = """
            SELECT v.id AS "vendorId",
                   ST_Distance(v.location, CAST(ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326) AS geography)) / 1000.0 AS "distanceKm"
              FROM vendors v
             WHERE v.id IN (:vendorIds)
               AND v.location IS NOT NULL
            """, nativeQuery = true)
    List<VendorDistance> straightLineDistancesKm(@Param("vendorIds") Collection<UUID> vendorIds,
                                                 @Param("latitude") double latitude,
                                                 @Param("longitude") double longitude);

    /** Projection row of {@link #straightLineDistancesKm}. */
    interface VendorDistance {
        UUID getVendorId();
        Double getDistanceKm();
    }
}
