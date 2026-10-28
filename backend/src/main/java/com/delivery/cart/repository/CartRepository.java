package com.delivery.cart.repository;

import com.delivery.cart.entity.Cart;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Provides persistence access for customer carts. */
public interface CartRepository extends JpaRepository<Cart, UUID> {
    Optional<Cart> findByTenantIdAndCustomerIdAndStatus(UUID tenantId, UUID customerId, String status);
    Optional<Cart> findByTenantIdAndId(UUID tenantId, UUID id);
    List<Cart> findByTenantIdAndStatus(UUID tenantId, String status);

    /** Loads a cart with SELECT ... FOR UPDATE so concurrent checkouts of the same cart run one after another. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.tenantId = :tenantId and c.id = :id")
    Optional<Cart> lockByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
