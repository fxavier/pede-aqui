package com.delivery.privacy.dto;

import java.time.Instant;
import java.util.UUID;

/** The caller's self-service profile settings (GET /me/profile, PATCH /me; W7-C). Returned only to the subject. */
public record ProfileSettingsResponse(
        UUID id,
        String email,
        String displayName,
        String phoneE164,
        Instant phoneVerifiedAt,
        String preferredLocale,
        NotificationPreferencesResponse notificationPreferences) {

    /** Effective notification opt-ins per external channel (the in-app inbox is always on). */
    public record NotificationPreferencesResponse(boolean push, boolean sms, boolean email) {}
}
