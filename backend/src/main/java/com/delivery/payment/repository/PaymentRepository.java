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
    Optional<Payment> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
    Optional<Payment> findByTenantIdAndOrderId(UUID tenantId, UUID orderId);
    List<Payment> findByTenantIdAndStatus(UUID tenantId, PaymentStatus status);
    List<Payment> findByTenantIdAndStatusIn(UUID tenantId, Collection<PaymentStatus> statuses);
    List<Payment> findByTenantId(UUID tenantId);
}
