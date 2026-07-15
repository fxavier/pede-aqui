package com.delivery.sales.dto;

import java.math.BigDecimal;

/** Immutable order-item snapshot line exposed by the sale detail view. */
public record SaleLineItemResponse(String productNameSnapshot, BigDecimal unitPriceSnapshot, int quantity, BigDecimal lineTotal) {}
