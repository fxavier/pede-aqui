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
    List<Product> findByTenantIdAndCategoryId(UUID tenantId, UUID categoryId);
    Optional<Product> findByTenantIdAndId(UUID tenantId, UUID id);

    /** Tenant-free: active products for one vendor (public browse). */
    List<Product> findByVendorIdAndStatus(UUID vendorId, String status);

    @Query("select distinct p.vendorId from Product p where p.tenantId = :tenantId and p.status = :status")
    List<UUID> findDistinctVendorIdsByTenantIdAndStatus(UUID tenantId, String status);

    @Query("select distinct p.vendorId from Product p where p.tenantId = :tenantId and p.categoryId = :categoryId and p.status = :status")
    List<UUID> findDistinctVendorIdsByTenantIdAndCategoryIdAndStatus(UUID tenantId, UUID categoryId, String status);

    /** Tenant-free: all active vendor IDs across all tenants (public browse). */
    @Query("select distinct p.vendorId from Product p where p.status = :status")
    List<UUID> findDistinctVendorIdsByStatus(String status);

    /** Tenant-free: active vendor IDs for a category across all tenants (public browse). */
    @Query("select distinct p.vendorId from Product p where p.categoryId = :categoryId and p.status = :status")
    List<UUID> findDistinctVendorIdsByCategoryIdAndStatus(UUID categoryId, String status);
}
