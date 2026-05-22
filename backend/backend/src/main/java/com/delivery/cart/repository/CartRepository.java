package com.delivery.cart.repository;

import com.delivery.cart.entity.Cart;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides persistence access for customer carts. */
public interface CartRepository extends JpaRepository<Cart, UUID> {
    Optional<Cart> findByTenantIdAndCustomerIdAndStatus(UUID tenantId, UUID customerId, String status);
    Optional<Cart> findByTenantIdAndId(UUID tenantId, UUID id);
    List<Cart> findByTenantIdAndStatus(UUID tenantId, String status);
}
