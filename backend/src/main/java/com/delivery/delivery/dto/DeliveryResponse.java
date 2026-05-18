package com.delivery.delivery.dto;

import com.delivery.delivery.entity.DeliveryStatus;
import java.util.UUID;

/** Delivery response containing status and failed code attempt count. */
public record DeliveryResponse(UUID id, UUID orderId, DeliveryStatus status, int confirmationAttempts) {}
