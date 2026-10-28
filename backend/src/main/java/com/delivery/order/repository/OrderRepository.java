package com.delivery.order.repository;

import com.delivery.order.entity.CancellationReason;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
    long countByTenantIdAndStatusAndCancellationReason(UUID tenantId, OrderStatus status, CancellationReason cancellationReason);
}
