package com.delivery.customer.repository;

import com.delivery.customer.entity.CustomerFavoriteVendor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for managing customer favorite vendors. */
public interface CustomerFavoriteVendorRepository extends JpaRepository<CustomerFavoriteVendor, UUID> {

    /** Find all favorite vendors for a customer within a tenant. */
    @Query("SELECT cfv FROM CustomerFavoriteVendor cfv WHERE cfv.tenantId = :tenantId AND cfv.customerId = :customerId ORDER BY cfv.createdAt DESC")
    List<CustomerFavoriteVendor> findByTenantIdAndCustomerId(@Param("tenantId") UUID tenantId, @Param("customerId") UUID customerId);

    /** Check if a vendor is favorited by a customer. */
    @Query("SELECT cfv FROM CustomerFavoriteVendor cfv WHERE cfv.tenantId = :tenantId AND cfv.customerId = :customerId AND cfv.vendorId = :vendorId")
    Optional<CustomerFavoriteVendor> findByTenantIdAndCustomerIdAndVendorId(
        @Param("tenantId") UUID tenantId, 
        @Param("customerId") UUID customerId, 
        @Param("vendorId") UUID vendorId
    );

    /** Get vendor IDs that are favorited by a customer. */
    @Query("SELECT cfv.vendorId FROM CustomerFavoriteVendor cfv WHERE cfv.tenantId = :tenantId AND cfv.customerId = :customerId")
    List<UUID> findVendorIdsByTenantIdAndCustomerId(@Param("tenantId") UUID tenantId, @Param("customerId") UUID customerId);

    /** Delete a favorite vendor relationship. */
    void deleteByTenantIdAndCustomerIdAndVendorId(UUID tenantId, UUID customerId, UUID vendorId);
}