package com.delivery.catalog.repository;

import com.delivery.catalog.entity.Sku;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides SKU persistence access. */
public interface SkuRepository extends JpaRepository<Sku, UUID> {
    Optional<Sku> findByTenantIdAndId(UUID tenantId, UUID id);
    List<Sku> findByTenantIdAndProductIdInAndActiveTrue(UUID tenantId, List<UUID> productIds);
    /** Tenant-free: active SKUs for given products (public browse). */
    List<Sku> findByProductIdInAndActiveTrue(List<UUID> productIds);
}
