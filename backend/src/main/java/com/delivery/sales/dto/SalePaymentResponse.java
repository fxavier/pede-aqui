package com.delivery.sales.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Payment summary attached to the sale detail view. */
public record SalePaymentResponse(UUID id, BigDecimal amount, String provider, String status) {}
