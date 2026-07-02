package com.delivery.catalog.repository;

import com.delivery.catalog.entity.ProductVariationOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVariationOptionRepository extends JpaRepository<ProductVariationOption, UUID> {

    List<ProductVariationOption> findByTenantIdAndGroupIdOrderByDisplayOrder(UUID tenantId, UUID groupId);
    List<ProductVariationOption> findByTenantIdAndGroupIdOrderByDisplayOrderAsc(UUID tenantId, UUID groupId);

    List<ProductVariationOption> findByTenantIdAndGroupId(UUID tenantId, UUID groupId);

    List<ProductVariationOption> findByTenantIdAndGroupIdAndAvailable(UUID tenantId, UUID groupId, Boolean available);
    
    Optional<ProductVariationOption> findByTenantIdAndId(UUID tenantId, UUID id);

    void deleteByTenantIdAndGroupId(UUID tenantId, UUID groupId);
}