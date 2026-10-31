package com.delivery.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Starts a payment attempt: the mobile-money phone number to push the charge to (E.164 or Mozambican national form). */
public record CreatePaymentAttemptRequest(@NotBlank @Size(max = 32) String msisdn) {
}
