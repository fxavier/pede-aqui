package com.delivery.finance.dto;

import com.delivery.payment.entity.RefundStatus;
import java.math.BigDecimal;
import java.util.UUID;

/** Refund item visible in finance backoffice views. */
public record RefundFinanceResponse(UUID id, UUID paymentId, UUID orderId, BigDecimal amount, String reason, RefundStatus status) {}
