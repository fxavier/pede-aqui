package com.delivery.inventory.service;

import com.delivery.common.exception.NotFoundException;
import com.delivery.inventory.dto.InventoryResponse;
import com.delivery.inventory.entity.InventoryItem;
import com.delivery.inventory.mapper.InventoryMapper;
import com.delivery.inventory.repository.InventoryItemRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contains stock update and reservation rules. */
@Service
public class InventoryService {
    private final InventoryItemRepository repository;
    private final InventoryMapper mapper;

    public InventoryService(InventoryItemRepository repository, InventoryMapper mapper) { this.repository = repository; this.mapper = mapper; }

    /** Updates available stock after validating it is not negative. */
    @Transactional
    public InventoryResponse updateAvailable(UUID inventoryItemId, int quantityAvailable) {
        InventoryItem item = repository.findById(inventoryItemId).orElseThrow(() -> new NotFoundException("Inventory item was not found"));
        item.updateAvailable(quantityAvailable);
        return mapper.toResponse(item);
    }

    /** Reserves stock for checkout using optimistic locking on the inventory row. */
    @Transactional
    public void reserve(UUID tenantId, UUID skuId, int quantity) {
        InventoryItem item = repository.findByTenantIdAndSkuId(tenantId, skuId).orElseThrow(() -> new NotFoundException("Inventory item was not found"));
        item.reserve(quantity);
    }
}
