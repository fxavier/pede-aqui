package com.delivery.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Cash-on-delivery reconciliation item for finance. */
public record CashReconciliationResponse(UUID id, UUID orderId, UUID deliveryId, UUID courierId, BigDecimal amount, String status, Instant recordedAt) {}
