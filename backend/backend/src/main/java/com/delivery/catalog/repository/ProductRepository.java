package com.delivery.catalog.repository;

import com.delivery.catalog.entity.Product;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Provides tenant and vendor scoped product queries. */
public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByTenantIdAndVendorId(UUID tenantId, UUID vendorId);
    List<Product> findByTenantIdAndVendorIdAndStatus(UUID tenantId, UUID vendorId, String status);
    List<Product> findByTenantIdAndCategoryIdAndStatus(UUID tenantId, UUID categoryId, String status);
    Optional<Product> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("select distinct p.vendorId from Product p where p.tenantId = :tenantId and p.status = :status")
    List<UUID> findDistinctVendorIdsByTenantIdAndStatus(UUID tenantId, String status);

    @Query("select distinct p.vendorId from Product p where p.tenantId = :tenantId and p.categoryId = :categoryId and p.status = :status")
    List<UUID> findDistinctVendorIdsByTenantIdAndCategoryIdAndStatus(UUID tenantId, UUID categoryId, String status);
}
