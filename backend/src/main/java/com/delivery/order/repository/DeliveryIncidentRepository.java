package com.delivery.order.repository;

import com.delivery.order.entity.DeliveryIncident;
import com.delivery.order.entity.DeliveryIncidentStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Delivery incidents (LAC-44, V070); every lookup is tenant-scoped. */
public interface DeliveryIncidentRepository extends JpaRepository<DeliveryIncident, UUID> {
    Optional<DeliveryIncident> findByTenantIdAndId(UUID tenantId, UUID id);

    /** The incident's order id only (scalar, so the entity is first loaded under its row lock, after the payment lock). */
    @Query("select i.orderId from DeliveryIncident i where i.tenantId = :tenantId and i.id = :id")
    Optional<UUID> findOrderIdByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /** Same row FOR UPDATE: serializes OPS resolutions of one incident. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from DeliveryIncident i where i.tenantId = :tenantId and i.id = :id")
    Optional<DeliveryIncident> findByTenantIdAndIdForUpdate(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /** The incident of one delivery (at most one: uq_delivery_incidents_delivery). */
    Optional<DeliveryIncident> findByTenantIdAndDeliveryId(UUID tenantId, UUID deliveryId);

    /** OPS queue page (idx_delivery_incidents_tenant_status_created); sort comes from the pageable. */
    Page<DeliveryIncident> findByTenantIdAndStatus(UUID tenantId, DeliveryIncidentStatus status, Pageable pageable);
}
