package com.delivery.payment.dto;

import com.delivery.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.util.UUID;

/** Payment response returned by payment endpoints. */
public record PaymentResponse(UUID id, UUID orderId, BigDecimal amount, PaymentStatus status) {}
