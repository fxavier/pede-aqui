package com.delivery.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Immutable snapshot of a single line item within an order response. */
public record OrderItemResponse(UUID id, String productName, String skuName, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {}
