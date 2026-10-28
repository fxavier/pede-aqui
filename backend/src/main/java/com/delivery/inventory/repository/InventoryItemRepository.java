package com.delivery.inventory.repository;

import com.delivery.inventory.entity.InventoryItem;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Provides inventory persistence access for SKU stock. */
public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {
    Optional<InventoryItem> findByTenantIdAndSkuId(UUID tenantId, UUID skuId);
    Optional<InventoryItem> findByTenantIdAndId(UUID tenantId, UUID id);

    /** Loads the SKU stock row with SELECT ... FOR UPDATE so concurrent stock mutations serialize on it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.tenantId = :tenantId and i.skuId = :skuId")
    Optional<InventoryItem> lockByTenantIdAndSkuId(@Param("tenantId") UUID tenantId, @Param("skuId") UUID skuId);
}
