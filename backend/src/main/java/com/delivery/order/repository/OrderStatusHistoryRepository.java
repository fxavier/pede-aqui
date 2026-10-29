package com.delivery.order.repository;

import com.delivery.order.entity.OrderStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Append-only order status history (V058); per-order reads use idx_order_status_history_order. */
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, UUID> {
    List<OrderStatusHistory> findByTenantIdAndOrderIdOrderByCreatedAtAscIdAsc(UUID tenantId, UUID orderId);
}
