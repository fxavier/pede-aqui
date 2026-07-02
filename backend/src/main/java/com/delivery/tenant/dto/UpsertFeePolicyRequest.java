package com.delivery.tenant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Request used to configure tenant fees, taxes, commission, and cancellation policy. */
public record UpsertFeePolicyRequest(
        @NotNull @DecimalMin("0.0") BigDecimal deliveryFee,
        @NotNull @DecimalMin("0.0") BigDecimal serviceFee,
        @NotNull @DecimalMin("0.0") BigDecimal taxRate,
        @NotNull @DecimalMin("0.0") BigDecimal commissionRate,
        @NotBlank String cancellationPolicy) {}
