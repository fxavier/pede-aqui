package com.delivery.sales.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Refund summary attached to the sale detail view. */
public record SaleRefundResponse(UUID id, BigDecimal amount, String status) {}
