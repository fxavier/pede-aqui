package com.delivery.cart.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Cart item response for one selected SKU. */
public record CartItemResponse(UUID skuId, String productName, String skuName, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {}
