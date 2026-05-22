package com.delivery.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Request used to create a total or partial refund. */
public record RefundRequest(@NotNull BigDecimal amount, @NotBlank String reason, @NotBlank String idempotencyKey) {}
