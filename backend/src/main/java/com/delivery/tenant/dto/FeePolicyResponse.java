package com.delivery.tenant.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Fee and policy configuration response for tenant operations. */
public record FeePolicyResponse(UUID id, BigDecimal deliveryFee, BigDecimal serviceFee, BigDecimal taxRate, BigDecimal commissionRate, String cancellationPolicy) {}
