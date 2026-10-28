package com.delivery.customer.repository;

import com.delivery.customer.entity.CustomerAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides customer address persistence; every lookup is scoped by tenant and owning customer. */
public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {
    List<CustomerAddress> findByTenantIdAndCustomerIdOrderByCreatedAtAsc(UUID tenantId, UUID customerId);
    Optional<CustomerAddress> findByTenantIdAndCustomerIdAndId(UUID tenantId, UUID customerId, UUID id);
    long countByTenantIdAndCustomerId(UUID tenantId, UUID customerId);
}
