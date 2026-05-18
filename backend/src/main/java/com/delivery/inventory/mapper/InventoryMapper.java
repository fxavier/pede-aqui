package com.delivery.inventory.mapper;

import com.delivery.inventory.dto.InventoryResponse;
import com.delivery.inventory.entity.InventoryItem;
import org.springframework.stereotype.Component;

/** Converts inventory entities to DTOs. */
@Component
public class InventoryMapper {
    public InventoryResponse toResponse(InventoryItem item) {
        return new InventoryResponse(item.getId(), item.getSkuId(), item.getQuantityAvailable(), item.getQuantityReserved());
    }
}
