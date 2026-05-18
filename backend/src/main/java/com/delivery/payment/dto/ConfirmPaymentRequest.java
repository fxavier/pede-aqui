package com.delivery.payment.dto;

import jakarta.validation.constraints.NotBlank;

/** Request used by the local mock provider to confirm payment. */
public record ConfirmPaymentRequest(@NotBlank String idempotencyKey) {}
