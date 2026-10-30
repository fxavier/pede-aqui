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
 *
 * <p>W6-A (optional, omitted = keep current): taxRate is the standard VAT rate (16 %); {@code taxInclusive} true (default)
 * = catalogue prices and fees include VAT (extracted), false = VAT added on top (pre-W6-A); {@code maxDeliveryDistanceKm}
 * caps the straight-line delivery distance at checkout (0 = remove the limit).
 */
public record UpsertFeePolicyRequest(
        @NotNull @DecimalMin("0.0") BigDecimal deliveryFee,
        @NotNull @DecimalMin("0.0") BigDecimal serviceFee,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal taxRate,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal commissionRate,
        @NotBlank String cancellationPolicy,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal serviceFeeRate,
        Boolean taxInclusive,
        @DecimalMin("0.0") @DecimalMax("999999.99") BigDecimal maxDeliveryDistanceKm) {

    /** Pre-W6-A shape (no VAT mode, no distance limit), kept for callers and tests. */
    public UpsertFeePolicyRequest(BigDecimal deliveryFee, BigDecimal serviceFee, BigDecimal taxRate, BigDecimal commissionRate,
                                  String cancellationPolicy, BigDecimal serviceFeeRate) {
        this(deliveryFee, serviceFee, taxRate, commissionRate, cancellationPolicy, serviceFeeRate, null, null);
    }
}
