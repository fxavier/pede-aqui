package com.delivery.dispatch.dto;

import java.time.Instant;
import java.util.UUID;

public record CourierDocumentResponse(
    UUID id,
    UUID courierId,
    String documentType,
    String storageKey,
    String status,
    Instant createdAt
) {}