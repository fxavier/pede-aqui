package com.delivery.payment.repository;

import com.delivery.payment.entity.Payment;
import com.delivery.payment.entity.PaymentStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Provides payment persistence access and idempotency lookup. */
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByTenantIdAndId(UUID tenantId, UUID id);

    /** Tenant-scoped lookup holding a row lock (SELECT ... FOR UPDATE) to serialize refunds against one payment (INC-06, COR-04). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.tenantId = :tenantId and p.id = :id")
    Optional<Payment> findByTenantIdAndIdForUpdate(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
    /** Same lock, looked up by order: serializes vendor rejections (and refunds) of one order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.tenantId = :tenantId and p.orderId = :orderId")
    Optional<Payment> findByTenantIdAndOrderIdForUpdate(@Param("tenantId") UUID tenantId, @Param("orderId") UUID orderId);
    /**
     * Non-blocking variant of the lock above for background jobs (LAC-46 claim pattern): locks the order's payment
     * row(s) FOR UPDATE SKIP LOCKED and returns their ids; a row held by a request in flight is skipped (empty result)
     * so the job moves on and retries on its next tick instead of queueing behind user traffic.
     */
    @Query(value = "SELECT p.id FROM payments p WHERE p.tenant_id = :tenantId AND p.order_id = :orderId FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<UUID> lockIdsByTenantIdAndOrderIdSkipLocked(@Param("tenantId") UUID tenantId, @Param("orderId") UUID orderId);
    boolean existsByTenantIdAndOrderId(UUID tenantId, UUID orderId);
    Optional<Payment> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
    Optional<Payment> findByTenantIdAndOrderId(UUID tenantId, UUID orderId);
    List<Payment> findByTenantIdAndStatus(UUID tenantId, PaymentStatus status);
    List<Payment> findByTenantIdAndStatusIn(UUID tenantId, Collection<PaymentStatus> statuses);
    List<Payment> findByTenantId(UUID tenantId);
    /** Reconciliation (W7-A): payments confirmed by a PSP (provider other than the mock) since {@code since}. */
    @Query("select p from Payment p where p.provider <> 'LOCAL_MOCK' and p.confirmedAt >= :since order by p.confirmedAt")
    List<Payment> findProviderConfirmedSince(@Param("since") java.time.Instant since, org.springframework.data.domain.Pageable page);
}
