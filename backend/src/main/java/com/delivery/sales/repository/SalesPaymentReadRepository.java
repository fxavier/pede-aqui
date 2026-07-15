package com.delivery.sales.repository;

import com.delivery.payment.entity.Payment;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Read-only payment lookups needed by the sales projection (no writes). */
public interface SalesPaymentReadRepository extends Repository<Payment, UUID> {
    List<PaymentSummary> findByTenantIdAndOrderIdIn(UUID tenantId, Collection<UUID> orderIds);
    List<PaymentSummary> findByTenantIdAndOrderId(UUID tenantId, UUID orderId);
}
