package com.delivery.payment.repository;

import com.delivery.payment.entity.PaymentStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Append-only payment status history (V058); per-order reads use idx_payment_status_history_order. */
public interface PaymentStatusHistoryRepository extends JpaRepository<PaymentStatusHistory, UUID> {
    List<PaymentStatusHistory> findByTenantIdAndOrderIdOrderByCreatedAtAscIdAsc(UUID tenantId, UUID orderId);
}
