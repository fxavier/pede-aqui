package com.delivery.payment.repository;

import com.delivery.payment.entity.Refund;
import com.delivery.payment.entity.RefundStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides refund persistence access. */
public interface RefundRepository extends JpaRepository<Refund, UUID> {
    Optional<Refund> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
    List<Refund> findByTenantIdAndStatus(UUID tenantId, RefundStatus status);
}
