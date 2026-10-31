package com.delivery.payment.repository;

import com.delivery.payment.entity.PaymentReconciliationCase;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reconciliation case persistence (LAC-32): idempotent insert by dedup key, tenant-scoped paged reads. */
public interface PaymentReconciliationCaseRepository extends JpaRepository<PaymentReconciliationCase, UUID> {

    /** Inserts the case unless one with the same dedup key exists; returns 1 when inserted. */
    @Modifying
    @Query(value = """
            INSERT INTO payment_reconciliation_cases (id, tenant_id, case_type, status, dedup_key, payment_id, attempt_id,
                order_id, provider, expected_amount, observed_amount, detail_code, detected_at, created_at, updated_at)
            VALUES (:id, :tenantId, :caseType, 'OPEN', :dedupKey, :paymentId, :attemptId, :orderId, :provider,
                :expectedAmount, :observedAmount, :detailCode, now(), now(), now())
            ON CONFLICT (dedup_key) DO NOTHING""", nativeQuery = true)
    int openIfAbsent(@Param("id") UUID id, @Param("tenantId") UUID tenantId, @Param("caseType") String caseType,
                     @Param("dedupKey") String dedupKey, @Param("paymentId") UUID paymentId, @Param("attemptId") UUID attemptId,
                     @Param("orderId") UUID orderId, @Param("provider") String provider,
                     @Param("expectedAmount") java.math.BigDecimal expectedAmount,
                     @Param("observedAmount") java.math.BigDecimal observedAmount, @Param("detailCode") String detailCode);

    Page<PaymentReconciliationCase> findByTenantIdAndStatus(UUID tenantId, PaymentReconciliationCase.Status status, Pageable page);

    Page<PaymentReconciliationCase> findByTenantId(UUID tenantId, Pageable page);

    Optional<PaymentReconciliationCase> findByTenantIdAndId(UUID tenantId, UUID id);

    long countByStatus(PaymentReconciliationCase.Status status);
}
