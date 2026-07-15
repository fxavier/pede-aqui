package com.delivery.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Request for a full (amount omitted) or partial refund through the existing finance path. */
public record SalesRefundRequest(BigDecimal amount, @NotBlank @Size(max = 500) String reason) {}
