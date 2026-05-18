package com.delivery.cart.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Cart response with item snapshots and calculated totals. */
public record CartResponse(UUID id, UUID vendorId, BigDecimal subtotal, BigDecimal fees, BigDecimal taxes, BigDecimal discounts, BigDecimal total, List<CartItemResponse> items) {}
