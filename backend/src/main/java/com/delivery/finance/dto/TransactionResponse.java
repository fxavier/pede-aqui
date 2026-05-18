package com.delivery.finance.dto;

import com.delivery.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.util.UUID;

/** Payment transaction item for finance users. */
public record TransactionResponse(UUID id, UUID orderId, BigDecimal amount, PaymentStatus status) {}
