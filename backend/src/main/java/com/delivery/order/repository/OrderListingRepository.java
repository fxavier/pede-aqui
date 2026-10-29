package com.delivery.order.repository;

import com.delivery.order.entity.Order;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Read-only keyset queries for order listing (LAC-14): rows strictly older than {@code (createdAt, id)}, newest
 * first, served by the V054 indexes. Kept apart from {@link OrderRepository} so listing changes never touch the
 * checkout persistence interface.
 */
public interface OrderListingRepository extends Repository<Order, UUID> {

    @Query("""
            select o from Order o
             where o.tenantId = :tenantId
               and (o.createdAt < :createdAt or (o.createdAt = :createdAt and o.id < :id))
             order by o.createdAt desc, o.id desc""")
    List<Order> findTenantKeysetPage(@Param("tenantId") UUID tenantId, @Param("createdAt") Instant createdAt,
                                     @Param("id") UUID id, Pageable limit);

    @Query("""
            select o from Order o
             where o.tenantId = :tenantId and o.vendorId = :vendorId
               and (o.createdAt < :createdAt or (o.createdAt = :createdAt and o.id < :id))
             order by o.createdAt desc, o.id desc""")
    List<Order> findVendorKeysetPage(@Param("tenantId") UUID tenantId, @Param("vendorId") UUID vendorId,
                                     @Param("createdAt") Instant createdAt, @Param("id") UUID id, Pageable limit);

    /** Platform super-admin view across tenants. */
    @Query("""
            select o from Order o
             where o.createdAt < :createdAt or (o.createdAt = :createdAt and o.id < :id)
             order by o.createdAt desc, o.id desc""")
    List<Order> findAllKeysetPage(@Param("createdAt") Instant createdAt, @Param("id") UUID id, Pageable limit);
}
