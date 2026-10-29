package com.delivery.auth.dto;

import com.delivery.common.security.MarketplaceRole;
import java.time.Instant;
import java.time.LocalDate;
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
        Set<MarketplaceRole> roles,
        String fullName,
        String nif,
        LocalDate dateOfBirth,
        String address,
        String avatarStorageKey,
        /* Verified phone in E.164 from the phone + SMS login (LAC-18); null when never verified. */
        String phoneE164,
        Instant phoneVerifiedAt) {
}
