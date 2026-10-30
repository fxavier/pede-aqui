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
    /** Orders of one page of another list (e.g. the cancellation-request queue), tenant-scoped. */
    List<Order> findByTenantIdAndIdIn(UUID tenantId, java.util.Collection<UUID> ids);
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

    /**
     * PaymentExpiryJob candidates (W4-A): orders still PAYMENT_PENDING created before {@code cutoff} whose payment is
     * still PENDING_CONFIRMATION (or that have none). Inconsistent rows (payment captured) are never candidates, so
     * they cannot starve the bounded batch; ops find them by query. Read-only, no lock (each one is re-checked under
     * the payment lock); oldest first, bounded. Served by V058 idx_orders_status_timeouts.
     */
    @Query(value = """
            SELECT o.id AS "orderId", o.tenant_id AS "tenantId"
              FROM orders o
              LEFT JOIN payments p ON p.tenant_id = o.tenant_id AND p.order_id = o.id
             WHERE o.status = 'PAYMENT_PENDING'
               AND o.created_at < :cutoff
               AND (p.id IS NULL OR p.status = 'PENDING_CONFIRMATION')
             ORDER BY o.created_at, o.id
             LIMIT :limit""", nativeQuery = true)
    List<StockReconciliationCandidate> findPaymentExpiryCandidates(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    /**
     * AcceptanceTimeoutJob candidates (W4-A): orders still PAYMENT_CONFIRMED whose payment was confirmed before
     * {@code cutoff} (rows without payments.confirmed_at fall back to orders.updated_at, the moment of the last
     * transition) and whose payment was captured and not fully refunded (CONFIRMED/PARTIALLY_REFUNDED — inconsistent
     * rows are never candidates, so they cannot starve the batch). Read-only, no lock; oldest first, bounded.
     */
    @Query(value = """
            SELECT o.id AS "orderId", o.tenant_id AS "tenantId"
              FROM orders o
              JOIN payments p ON p.tenant_id = o.tenant_id AND p.order_id = o.id
             WHERE o.status = 'PAYMENT_CONFIRMED'
               AND p.status IN ('CONFIRMED', 'PARTIALLY_REFUNDED')
               AND COALESCE(p.confirmed_at, o.updated_at) < :cutoff
             ORDER BY o.created_at, o.id
             LIMIT :limit""", nativeQuery = true)
    List<StockReconciliationCandidate> findAcceptanceTimeoutCandidates(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    /** Projection (tenant id + order id) of the job candidate queries above. */
    interface StockReconciliationCandidate {
        UUID getOrderId();
        UUID getTenantId();
    }

    long countByTenantIdAndStatusAndCancellationReason(UUID tenantId, OrderStatus status, CancellationReason cancellationReason);
}
