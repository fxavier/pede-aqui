package com.delivery.sales.repository;

import com.delivery.finance.entity.Commission;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Read-only commission lookups for the sale detail view (no writes). */
public interface SalesCommissionReadRepository extends Repository<Commission, UUID> {
    List<Commission> findByTenantIdAndOrderId(UUID tenantId, UUID orderId);
}
