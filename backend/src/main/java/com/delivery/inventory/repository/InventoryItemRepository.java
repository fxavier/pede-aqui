package com.delivery.inventory.repository;

import com.delivery.inventory.entity.InventoryItem;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides inventory persistence access for SKU stock. */
public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {
    Optional<InventoryItem> findByTenantIdAndSkuId(UUID tenantId, UUID skuId);
}
