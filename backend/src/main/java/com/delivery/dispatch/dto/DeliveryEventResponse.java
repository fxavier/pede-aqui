package com.delivery.dispatch.dto;

import java.time.Instant;
import java.util.UUID;

/** Delivery event response used by operations order monitoring views. */
public record DeliveryEventResponse(UUID id, UUID deliveryId, String eventType, String notes, Instant createdAt) {}
