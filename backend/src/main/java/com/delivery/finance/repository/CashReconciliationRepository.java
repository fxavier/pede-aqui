package com.delivery.finance.repository;

import com.delivery.finance.entity.CashReconciliation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides cash reconciliation queries for finance views. */
public interface CashReconciliationRepository extends JpaRepository<CashReconciliation, UUID> {
    List<CashReconciliation> findByTenantId(UUID tenantId);
    List<CashReconciliation> findByTenantIdAndStatus(UUID tenantId, String status);
    List<CashReconciliation> findByTenantIdAndCourierId(UUID tenantId, UUID courierId);
    List<CashReconciliation> findByTenantIdAndRecordedAtBetween(UUID tenantId, Instant from, Instant to);
}
