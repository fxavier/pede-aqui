package com.delivery.inventory.dto;

import java.util.UUID;

/** Inventory response showing available and reserved stock. */
public record InventoryResponse(UUID id, UUID skuId, int quantityAvailable, int quantityReserved) {}
