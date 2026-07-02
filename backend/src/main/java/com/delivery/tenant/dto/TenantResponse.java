package com.delivery.tenant.dto;

import java.time.Instant;
import java.util.UUID;

/** Response containing tenant configuration visible to administrators. */
public record TenantResponse(
        UUID id,
        String name,
        String slug,
        String status,
        String defaultCurrency,
        Instant createdAt,
        Instant updatedAt) {
}
