package com.delivery.payment.repository;

import com.delivery.payment.entity.Payment;
import com.delivery.payment.entity.PaymentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides payment persistence access and idempotency lookup. */
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<Payment> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
    Optional<Payment> findByTenantIdAndOrderId(UUID tenantId, UUID orderId);
    List<Payment> findByTenantIdAndStatus(UUID tenantId, PaymentStatus status);
}
