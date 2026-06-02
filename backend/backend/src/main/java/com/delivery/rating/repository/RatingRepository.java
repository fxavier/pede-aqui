package com.delivery.rating.repository;

import com.delivery.rating.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RatingRepository extends JpaRepository<Rating, UUID> {

    Optional<Rating> findByTenantIdAndOrderIdAndRaterUserId(UUID tenantId, UUID orderId, UUID raterUserId);

    List<Rating> findByTenantIdAndRatedVendorId(UUID tenantId, UUID ratedVendorId);

    List<Rating> findByTenantIdAndRatedCourierId(UUID tenantId, UUID ratedCourierId);

    List<Rating> findByTenantIdAndRaterUserId(UUID tenantId, UUID raterUserId);

    @Query("SELECT AVG(r.stars) FROM Rating r WHERE r.tenantId = :tenantId AND r.ratedVendorId = :vendorId")
    Double getAverageRatingForVendor(@Param("tenantId") UUID tenantId, @Param("vendorId") UUID vendorId);

    @Query("SELECT AVG(r.stars) FROM Rating r WHERE r.tenantId = :tenantId AND r.ratedCourierId = :courierId")
    Double getAverageRatingForCourier(@Param("tenantId") UUID tenantId, @Param("courierId") UUID courierId);

    @Query("SELECT COUNT(r) FROM Rating r WHERE r.tenantId = :tenantId AND r.ratedVendorId = :vendorId")
    long countRatingsForVendor(@Param("tenantId") UUID tenantId, @Param("vendorId") UUID vendorId);

    @Query("SELECT COUNT(r) FROM Rating r WHERE r.tenantId = :tenantId AND r.ratedCourierId = :courierId")
    long countRatingsForCourier(@Param("tenantId") UUID tenantId, @Param("courierId") UUID courierId);
}