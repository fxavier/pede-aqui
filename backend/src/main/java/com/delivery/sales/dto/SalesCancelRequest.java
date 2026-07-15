package com.delivery.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request to cancel a sale in a pre-dispatch state (reason is mandatory for the audit trail). */
public record SalesCancelRequest(@NotBlank @Size(max = 500) String reason) {}
