package com.delivery.catalog.repository;

import com.delivery.catalog.entity.ProductVariationGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVariationGroupRepository extends JpaRepository<ProductVariationGroup, UUID> {

    List<ProductVariationGroup> findByTenantIdAndProductIdOrderByDisplayOrder(UUID tenantId, UUID productId);
    List<ProductVariationGroup> findByTenantIdAndProductIdOrderByDisplayOrderAsc(UUID tenantId, UUID productId);

    List<ProductVariationGroup> findByTenantIdAndProductId(UUID tenantId, UUID productId);
    
    Optional<ProductVariationGroup> findByTenantIdAndId(UUID tenantId, UUID id);

    void deleteByTenantIdAndProductId(UUID tenantId, UUID productId);
}