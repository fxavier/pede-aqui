package com.delivery.auth.dto;

import java.util.UUID;

/**
 * Response for successful merchant registration. Since ADR-0001 step 4, {@code tenantId}/{@code tenantSlug} are the
 * operator tenant's (no tenant is created) and the new vendor is described by {@code vendorId} and
 * {@code vendorVerificationStatus} (PENDING until an admin approves it).
 */
public record MerchantRegistrationResponse(
        UUID tenantId,
        String tenantSlug,
        UUID userProfileId,
        String email,
        String displayName,
        UUID vendorId,
        String vendorVerificationStatus) {
}
