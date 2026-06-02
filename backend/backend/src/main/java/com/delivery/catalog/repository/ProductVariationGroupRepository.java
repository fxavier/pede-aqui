package com.delivery.catalog.repository;

import com.delivery.catalog.entity.ProductVariationGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductVariationGroupRepository extends JpaRepository<ProductVariationGroup, UUID> {

    List<ProductVariationGroup> findByTenantIdAndProductIdOrderByDisplayOrder(UUID tenantId, UUID productId);

    List<ProductVariationGroup> findByTenantIdAndProductId(UUID tenantId, UUID productId);

    void deleteByTenantIdAndProductId(UUID tenantId, UUID productId);
}