package com.delivery.catalog.repository;

import com.delivery.catalog.entity.ServiceSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ServiceSlotRepository extends JpaRepository<ServiceSlot, UUID> {

    List<ServiceSlot> findByTenantIdAndVendorId(UUID tenantId, UUID vendorId);

    List<ServiceSlot> findByTenantIdAndVendorIdAndAvailable(UUID tenantId, UUID vendorId, Boolean available);

    List<ServiceSlot> findByTenantIdAndVendorIdAndDayOfWeek(UUID tenantId, UUID vendorId, Integer dayOfWeek);

    List<ServiceSlot> findByTenantIdAndProductId(UUID tenantId, UUID productId);

    @Query("SELECT s FROM ServiceSlot s WHERE s.tenantId = :tenantId AND s.vendorId = :vendorId " +
           "AND s.dayOfWeek = :dayOfWeek AND s.available = true AND s.bookedCount < s.capacity")
    List<ServiceSlot> findAvailableSlotsForVendorAndDay(@Param("tenantId") UUID tenantId, 
                                                        @Param("vendorId") UUID vendorId, 
                                                        @Param("dayOfWeek") Integer dayOfWeek);

    @Query("SELECT s FROM ServiceSlot s WHERE s.tenantId = :tenantId AND s.vendorId = :vendorId " +
           "AND s.dayOfWeek = :dayOfWeek AND s.startTime <= :time AND s.endTime > :time")
    List<ServiceSlot> findSlotsByVendorDayAndTime(@Param("tenantId") UUID tenantId,
                                                  @Param("vendorId") UUID vendorId,
                                                  @Param("dayOfWeek") Integer dayOfWeek,
                                                  @Param("time") LocalTime time);

    void deleteByTenantIdAndVendorId(UUID tenantId, UUID vendorId);

    void deleteByTenantIdAndProductId(UUID tenantId, UUID productId);
}