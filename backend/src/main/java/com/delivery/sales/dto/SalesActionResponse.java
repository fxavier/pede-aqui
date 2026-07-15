package com.delivery.sales.dto;

import java.util.UUID;

/** Minimal acknowledgement returned by sales actions (never carries secrets such as the delivery OTP). */
public record SalesActionResponse(UUID orderId, String reference, String orderStatus) {}
