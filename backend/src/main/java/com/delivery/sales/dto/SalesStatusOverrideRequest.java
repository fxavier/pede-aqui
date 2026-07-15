package com.delivery.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request for a config-gated, allow-listed manual status transition (reason mandatory). */
public record SalesStatusOverrideRequest(@NotBlank String targetStatus, @NotBlank @Size(max = 500) String reason) {}
