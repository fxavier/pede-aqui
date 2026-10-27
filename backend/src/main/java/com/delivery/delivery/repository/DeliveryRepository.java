package com.delivery.delivery.repository;

import com.delivery.delivery.entity.Delivery;
import com.delivery.delivery.entity.DeliveryStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides delivery persistence access. */
public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {
    Optional<Delivery> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<Delivery> findByTenantIdAndOrderId(UUID tenantId, UUID orderId);
    List<Delivery> findByTenantIdAndStatus(UUID tenantId, DeliveryStatus status);
    List<Delivery> findByTenantIdAndCourierIdAndStatus(UUID tenantId, UUID courierId, DeliveryStatus status);
    /** True when the courier holds a delivery in one of the given statuses (used for dispatch exclusivity). */
    boolean existsByTenantIdAndCourierIdAndStatusIn(UUID tenantId, UUID courierId, Collection<DeliveryStatus> statuses);
}
