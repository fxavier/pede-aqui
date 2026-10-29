package com.delivery.order.repository;

import com.delivery.order.entity.CancellationReason;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Provides tenant-scoped order persistence access. */
public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByTenantId(UUID tenantId);
    Page<Order> findByTenantId(UUID tenantId, Pageable pageable);
    List<Order> findByTenantIdAndVendorId(UUID tenantId, UUID vendorId);
    Page<Order> findByTenantIdAndVendorId(UUID tenantId, UUID vendorId, Pageable pageable);
    Optional<Order> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<Order> findByTenantIdAndCheckoutIdempotencyKey(UUID tenantId, String checkoutIdempotencyKey);
    List<Order> findByTenantIdAndStatus(UUID tenantId, OrderStatus status);
    long countByTenantIdAndStatus(UUID tenantId, OrderStatus status);

    /**
     * ReservationReconciler candidates (LAC-23): orders whose stock is still RESERVED although the order is terminal
     * or its goods left the store — the same population the V043 data fix classified once. Read-only, no lock: each
     * candidate is re-checked under the payment lock before anything changes. Oldest first, bounded by {@code limit}.
     * Orders updated after {@code cutoff} are left to the request that is changing them.
     */
    @Query(value = """
            SELECT o.id AS "orderId", o.tenant_id AS "tenantId"
              FROM orders o
              LEFT JOIN deliveries d ON d.tenant_id = o.tenant_id AND d.order_id = o.id
             WHERE o.stock_status = 'RESERVED'
               AND o.updated_at < :cutoff
               AND (o.status IN ('PICKED_UP', 'DELIVERING', 'DELIVERED', 'CANCELLED', 'REJECTED_BY_VENDOR', 'REFUNDED')
                    OR d.status IN ('PICKED_UP', 'ON_ROUTE_TO_CUSTOMER', 'ARRIVED_AT_CUSTOMER', 'DELIVERED', 'FAILED_DELIVERY'))
             ORDER BY o.updated_at, o.id
             LIMIT :limit""", nativeQuery = true)
    List<StockReconciliationCandidate> findStockReconciliationCandidates(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    /** Status of the order's delivery row, if any (read by table so the order module needs no delivery entity type). */
    @Query(value = "SELECT d.status FROM deliveries d WHERE d.tenant_id = :tenantId AND d.order_id = :orderId", nativeQuery = true)
    Optional<String> findDeliveryStatus(@Param("tenantId") UUID tenantId, @Param("orderId") UUID orderId);

    /** Projection of {@link #findStockReconciliationCandidates}. */
    interface StockReconciliationCandidate {
        UUID getOrderId();
        UUID getTenantId();
    }

    long countByTenantIdAndStatusAndCancellationReason(UUID tenantId, OrderStatus status, CancellationReason cancellationReason);
}
