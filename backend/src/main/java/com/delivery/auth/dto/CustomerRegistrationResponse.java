package com.delivery.auth.dto;

/** Response after successful customer self-registration. */
public record CustomerRegistrationResponse(
        String keycloakUserId,
        String email,
        String displayName) {
}
