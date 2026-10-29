package com.delivery.order.dto;

import jakarta.validation.constraints.Size;

/** Optional body of POST /api/v1/me/orders/{id}/cancel: the customer's reason (kept only on a cancellation request). */
public record CancelOrderRequest(@Size(max = 500) String reason) {}
