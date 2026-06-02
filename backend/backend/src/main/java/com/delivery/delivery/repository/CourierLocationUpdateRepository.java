package com.delivery.delivery.repository;

import com.delivery.delivery.entity.CourierLocationUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourierLocationUpdateRepository extends JpaRepository<CourierLocationUpdate, UUID> {

    List<CourierLocationUpdate> findByTenantIdAndDeliveryIdOrderByRecordedAtDesc(UUID tenantId, UUID deliveryId);

    List<CourierLocationUpdate> findByTenantIdAndCourierIdOrderByRecordedAtDesc(UUID tenantId, UUID courierId);

    @Query("SELECT c FROM CourierLocationUpdate c WHERE c.tenantId = :tenantId AND c.deliveryId = :deliveryId " +
           "ORDER BY c.recordedAt DESC LIMIT 1")
    Optional<CourierLocationUpdate> findLatestLocationForDelivery(@Param("tenantId") UUID tenantId,
                                                                  @Param("deliveryId") UUID deliveryId);

    @Query("SELECT c FROM CourierLocationUpdate c WHERE c.tenantId = :tenantId AND c.courierId = :courierId " +
           "ORDER BY c.recordedAt DESC LIMIT 1")
    Optional<CourierLocationUpdate> findLatestLocationForCourier(@Param("tenantId") UUID tenantId,
                                                                 @Param("courierId") UUID courierId);

    @Query("SELECT c FROM CourierLocationUpdate c WHERE c.tenantId = :tenantId AND c.deliveryId = :deliveryId " +
           "AND c.recordedAt BETWEEN :startTime AND :endTime ORDER BY c.recordedAt ASC")
    List<CourierLocationUpdate> findLocationHistoryForDelivery(@Param("tenantId") UUID tenantId,
                                                               @Param("deliveryId") UUID deliveryId,
                                                               @Param("startTime") Instant startTime,
                                                               @Param("endTime") Instant endTime);

    void deleteByTenantIdAndDeliveryId(UUID tenantId, UUID deliveryId);
}