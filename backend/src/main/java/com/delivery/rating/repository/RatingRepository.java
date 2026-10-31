package com.delivery.rating.repository;

import com.delivery.rating.entity.Rating;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence of ratings plus the aggregate queries the rating aggregator recomputes from (LAC-28). */
public interface RatingRepository extends JpaRepository<Rating, UUID> {

    Optional<Rating> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<Rating> findByTenantIdAndOrderIdAndRaterUserId(UUID tenantId, UUID orderId, UUID raterUserId);

    /** Average and count of a vendor's stars: one row {@code [Double avg, Long count]} (avg null when count is 0). */
    @Query("SELECT AVG(r.stars), COUNT(r) FROM Rating r WHERE r.tenantId = :tenantId AND r.ratedVendorId = :vendorId")
    List<Object[]> vendorStats(@Param("tenantId") UUID tenantId, @Param("vendorId") UUID vendorId);

    /** Average and count of a courier's stars (rows with courier stars only). */
    @Query("SELECT AVG(r.courierStars), COUNT(r) FROM Rating r "
            + "WHERE r.tenantId = :tenantId AND r.ratedCourierId = :courierId AND r.courierStars IS NOT NULL")
    List<Object[]> courierStats(@Param("tenantId") UUID tenantId, @Param("courierId") UUID courierId);

    /** Keyset page of a vendor's ratings, newest first (created_at, id); the first page uses KeysetCursor.START. */
    @Query("SELECT r FROM Rating r WHERE r.tenantId = :tenantId AND r.ratedVendorId = :vendorId "
            + "AND (r.createdAt < :createdAt OR (r.createdAt = :createdAt AND r.id < :id)) "
            + "ORDER BY r.createdAt DESC, r.id DESC")
    List<Rating> findVendorPage(@Param("tenantId") UUID tenantId, @Param("vendorId") UUID vendorId,
                                @Param("createdAt") Instant createdAt, @Param("id") UUID id, Pageable pageable);

    /** Keyset page of the tenant's ratings for moderation, optionally filtered by vendor and hidden state. */
    @Query("SELECT r FROM Rating r WHERE r.tenantId = :tenantId "
            + "AND (:vendorId IS NULL OR r.ratedVendorId = :vendorId) "
            + "AND (:hidden IS NULL OR r.commentHidden = :hidden) "
            + "AND (r.createdAt < :createdAt OR (r.createdAt = :createdAt AND r.id < :id)) "
            + "ORDER BY r.createdAt DESC, r.id DESC")
    List<Rating> findModerationPage(@Param("tenantId") UUID tenantId, @Param("vendorId") UUID vendorId,
                                    @Param("hidden") Boolean hidden, @Param("createdAt") Instant createdAt,
                                    @Param("id") UUID id, Pageable pageable);
}
