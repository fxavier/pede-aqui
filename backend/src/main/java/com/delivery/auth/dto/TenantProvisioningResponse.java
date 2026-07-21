package com.delivery.auth.dto;

import java.util.UUID;

/** Descriptor of a provisioned (or pre-existing) tenant returned to the subscriptions platform. */
public record TenantProvisioningResponse(
        UUID tenantId,
        String tenantSlug,
        UUID ownerUserId,
        String ownerEmail,
        String displayName,
        String status,
        boolean provisioned,
        boolean alreadyExisted) {
}
