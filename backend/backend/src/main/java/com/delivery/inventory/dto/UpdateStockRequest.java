package com.delivery.inventory.dto;

import jakarta.validation.constraints.Min;

/** Request used to replace available stock quantity. */
public record UpdateStockRequest(@Min(0) int quantityAvailable) {}
