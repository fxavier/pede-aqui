package com.delivery.order.repository;

import com.delivery.order.entity.Order;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Read-only queries behind the customer's own order history (LAC-21): every query is scoped by tenant AND the
 * customer's profile id, so another customer's order is simply not found. Kept apart from {@link OrderRepository}
 * so the checkout persistence interface stays untouched.
 */
public interface CustomerOrderQueryRepository extends Repository<Order, UUID> {

    /** Keyset page (LAC-14 convention): rows strictly older than {@code (createdAt, id)}, newest first. */
    @Query("""
            select o from Order o
             where o.tenantId = :tenantId and o.customerId = :customerId
               and (o.createdAt < :createdAt or (o.createdAt = :createdAt and o.id < :id))
             order by o.createdAt desc, o.id desc""")
    List<Order> findCustomerKeysetPage(@Param("tenantId") UUID tenantId, @Param("customerId") UUID customerId,
                                       @Param("createdAt") Instant createdAt, @Param("id") UUID id, Pageable limit);

    /** The order only when it belongs to this customer in this tenant. */
    Optional<Order> findByTenantIdAndIdAndCustomerId(UUID tenantId, UUID id, UUID customerId);

    /** Item-line count per order for one page of orders, in one query (avoids loading every item collection). */
    @Query("select i.order.id, count(i) from OrderItem i where i.order.id in :orderIds group by i.order.id")
    List<Object[]> countItemsByOrderIds(@Param("orderIds") Collection<UUID> orderIds);
}
