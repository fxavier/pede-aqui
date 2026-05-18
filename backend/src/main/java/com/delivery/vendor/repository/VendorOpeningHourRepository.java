package com.delivery.vendor.repository;

import com.delivery.vendor.entity.VendorOpeningHour;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides persistence access for vendor opening-hour metadata. */
public interface VendorOpeningHourRepository extends JpaRepository<VendorOpeningHour, UUID> {
    List<VendorOpeningHour> findByTenantIdAndVendorIdOrderByDayOfWeekAsc(UUID tenantId, UUID vendorId);
    void deleteByTenantIdAndVendorId(UUID tenantId, UUID vendorId);
}
