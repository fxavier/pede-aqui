package com.delivery.auth.dto;

import com.delivery.common.security.MarketplaceRole;
import java.util.Set;
import java.util.UUID;

/** Response containing the current authenticated user's profile and roles. */
public record MeResponse(
        UUID id,
        UUID tenantId,
        String keycloakUserId,
        String email,
        String displayName,
        String phone,
        Set<MarketplaceRole> roles) {
}
