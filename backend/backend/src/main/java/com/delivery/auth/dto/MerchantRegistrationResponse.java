package com.delivery.auth.dto;

import java.util.UUID;

/** Response for successful merchant registration. */
public record MerchantRegistrationResponse(
        UUID tenantId,
        String tenantSlug,
        UUID userProfileId,
        String email,
        String displayName) {
}