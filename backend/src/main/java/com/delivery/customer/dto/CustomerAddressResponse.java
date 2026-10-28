package com.delivery.customer.dto;

import java.time.Instant;
import java.util.UUID;

/** A saved address as returned to its owner. */
public record CustomerAddressResponse(
        UUID id,
        String label,
        String recipientName,
        String phone,
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        Double latitude,
        Double longitude,
        String deliveryInstructions,
        Instant createdAt,
        Instant updatedAt) {}
