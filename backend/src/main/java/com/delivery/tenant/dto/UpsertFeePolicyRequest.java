package com.delivery.tenant.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Request used to configure tenant fees, taxes, commission, and cancellation policy. Rates are fractions
 * (0.08 = 8 %). serviceFee is a flat amount; serviceFeeRate (optional, INC-13) a percentage of the subtotal —
 * omitted keeps the current rate (0 for a new policy).
 */
public record UpsertFeePolicyRequest(
        @NotNull @DecimalMin("0.0") BigDecimal deliveryFee,
        @NotNull @DecimalMin("0.0") BigDecimal serviceFee,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal taxRate,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal commissionRate,
        @NotBlank String cancellationPolicy,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal serviceFeeRate) {}
