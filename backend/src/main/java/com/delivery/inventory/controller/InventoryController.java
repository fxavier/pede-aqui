package com.delivery.inventory.controller;

import com.delivery.inventory.dto.InventoryResponse;
import com.delivery.inventory.dto.UpdateStockRequest;
import com.delivery.inventory.service.InventoryService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes inventory stock management endpoints. */
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final InventoryService service;

    public InventoryController(InventoryService service) { this.service = service; }

    @PatchMapping("/{inventoryItemId}/stock")
    public InventoryResponse updateStock(@PathVariable UUID inventoryItemId, @Valid @RequestBody UpdateStockRequest request) {
        return service.updateAvailable(inventoryItemId, request.quantityAvailable());
    }
}
