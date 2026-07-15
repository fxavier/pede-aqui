package com.delivery.sales.repository;

import com.delivery.payment.entity.Refund;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Read-only refund lookups for the sales detail view and the refund cap/idempotency checks (no writes). */
public interface SalesRefundReadRepository extends Repository<Refund, UUID> {
    List<Refund> findByTenantIdAndOrderId(UUID tenantId, UUID orderId);
    Optional<Refund> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}
