package com.delivery.dispatch.dto;

import jakarta.validation.constraints.NotBlank;

/** Request used when a courier rejects an assignment. */
public record RejectDispatchRequest(@NotBlank String reason) {}
