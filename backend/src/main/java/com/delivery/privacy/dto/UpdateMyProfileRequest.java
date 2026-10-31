package com.delivery.privacy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH /api/v1/me (W7-C): every field is optional and null means "unchanged". E-mail and phone are deliberately not
 * here — they change through Keycloak (account console / e-mail verification; phone + SMS login, LAC-18).
 */
public record UpdateMyProfileRequest(
        @Size(min = 1, max = 120) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String displayName,
        @Size(max = 10) String preferredLocale,
        @Valid NotificationPreferencesPatch notificationPreferences) {

    /** Channel opt-ins to change; null = unchanged. */
    public record NotificationPreferencesPatch(Boolean push, Boolean sms, Boolean email) {}
}
