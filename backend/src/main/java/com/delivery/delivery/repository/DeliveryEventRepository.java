package com.delivery.delivery.repository;

import com.delivery.delivery.entity.DeliveryEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides delivery event persistence access. */
public interface DeliveryEventRepository extends JpaRepository<DeliveryEvent, UUID> {
    List<DeliveryEvent> findByTenantIdAndDeliveryIdOrderByCreatedAtAsc(UUID tenantId, UUID deliveryId);
}
