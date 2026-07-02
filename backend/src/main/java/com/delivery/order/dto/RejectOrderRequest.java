package com.delivery.order.dto;

import jakarta.validation.constraints.NotBlank;

/** Request used when a vendor rejects an order and must provide a reason. */
public record RejectOrderRequest(@NotBlank String reason) {}
