package com.delivery.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Commission item shown in finance endpoints. */
public record CommissionResponse(UUID id, UUID orderId, UUID vendorId, BigDecimal commissionAmount, String status, Instant createdAt) {}
