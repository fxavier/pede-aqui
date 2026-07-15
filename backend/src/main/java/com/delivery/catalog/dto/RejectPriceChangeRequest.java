package com.delivery.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Rejection payload for a pending price change; a reason is mandatory. */
public record RejectPriceChangeRequest(
        @NotBlank @Size(max = 500) String reason) {}
