package com.delivery.sales.dto;

import jakarta.validation.constraints.NotNull;

/** Request to re-send a customer notification for an order. */
public record SalesResendNotificationRequest(@NotNull SalesNotificationType type) {}
