package com.delivery.order.repository;

import com.delivery.order.entity.CancellationRequest;
import com.delivery.order.entity.CancellationRequestStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Customer cancellation requests (LAC-26, V059); every lookup is tenant-scoped. */
public interface CancellationRequestRepository extends JpaRepository<CancellationRequest, UUID> {
    Optional<CancellationRequest> findByTenantIdAndId(UUID tenantId, UUID id);

    /** The request's order id only (scalar, so the entity is first loaded under its row lock, after the payment lock). */
    @Query("select r.orderId from CancellationRequest r where r.tenantId = :tenantId and r.id = :id")
    Optional<UUID> findOrderIdByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /** Same row FOR UPDATE: serializes OPS decisions on one request. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CancellationRequest r where r.tenantId = :tenantId and r.id = :id")
    Optional<CancellationRequest> findByTenantIdAndIdForUpdate(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /** The order's open request, if any (at most one: uq_cancellation_requests_open_per_order). */
    Optional<CancellationRequest> findByTenantIdAndOrderIdAndStatus(UUID tenantId, UUID orderId, CancellationRequestStatus status);

    /** The order's most recent request (customer order detail, idempotent repeats). */
    Optional<CancellationRequest> findFirstByTenantIdAndOrderIdOrderByRequestedAtDescIdDesc(UUID tenantId, UUID orderId);

    /** OPS queue page (idx_cancellation_requests_tenant_status); sort comes from the pageable. */
    Page<CancellationRequest> findByTenantIdAndStatus(UUID tenantId, CancellationRequestStatus status, Pageable pageable);
}
