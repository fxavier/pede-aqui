package com.delivery.privacy.service;

import com.delivery.auth.api.UserAccounts;
import com.delivery.auth.api.UserAccounts.SelfProfile;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.service.AuditActions;
import com.delivery.common.service.AuditLogService;
import com.delivery.notification.api.NotificationPreferences;
import com.delivery.notification.api.NotificationPreferences.Channels;
import com.delivery.privacy.config.PrivacyProperties;
import com.delivery.privacy.dto.ProfileSettingsResponse;
import com.delivery.privacy.dto.UpdateMyProfileRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service profile settings (LAC-19, W7-C): display name and preferred locale (mirrored to Keycloak) and the
 * notification channel opt-ins (the preferences endpoint LAC-48 left open). The change is audited with the names of
 * the changed fields only — never their values.
 */
@Service
public class ProfileSettingsService {
    private final DataSubjectResolver subjects;
    private final UserAccounts userAccounts;
    private final NotificationPreferences notificationPreferences;
    private final AuditLogService auditLogService;
    private final PrivacyProperties properties;

    public ProfileSettingsService(DataSubjectResolver subjects, UserAccounts userAccounts,
                                  NotificationPreferences notificationPreferences, AuditLogService auditLogService,
                                  PrivacyProperties properties) {
        this.subjects = subjects;
        this.userAccounts = userAccounts;
        this.notificationPreferences = notificationPreferences;
        this.auditLogService = auditLogService;
        this.properties = properties;
    }

    /** The caller's current settings. */
    @Transactional(readOnly = true)
    public ProfileSettingsResponse current() {
        SelfProfile subject = subjects.currentSubject();
        return toResponse(subject, notificationPreferences.get(subject.tenantId(), subject.profileId()));
    }

    /**
     * Applies a partial update in one transaction: profile fields (+ Keycloak sync; a Keycloak failure rolls the whole
     * change back), then the notification opt-ins. Unsupported locale → 400 {@code unsupported_locale}.
     */
    @Transactional
    public ProfileSettingsResponse update(UpdateMyProfileRequest request) {
        SelfProfile subject = subjects.currentSubject();
        String displayName = request.displayName() == null ? null : request.displayName().trim();
        String locale = request.preferredLocale();
        if (locale != null && !properties.getSupportedLocales().contains(locale)) {
            throw new BusinessException("unsupported_locale",
                    "Unsupported locale; supported: " + String.join(", ", properties.getSupportedLocales()), HttpStatus.BAD_REQUEST);
        }
        List<String> changed = new ArrayList<>();
        if (displayName != null && !displayName.equals(subject.displayName())) {
            changed.add("displayName");
        } else {
            displayName = null;
        }
        if (locale != null && !locale.equals(subject.preferredLanguage())) {
            changed.add("preferredLocale");
        } else {
            locale = null;
        }
        SelfProfile updated = userAccounts.updateSelf(subject.tenantId(), subject.profileId(), displayName, locale);

        Channels channels;
        UpdateMyProfileRequest.NotificationPreferencesPatch patch = request.notificationPreferences();
        if (patch != null && (patch.push() != null || patch.sms() != null || patch.email() != null)) {
            Channels before = notificationPreferences.get(subject.tenantId(), subject.profileId());
            channels = notificationPreferences.update(subject.tenantId(), subject.profileId(), patch.push(), patch.sms(), patch.email());
            if (!channels.equals(before)) {
                changed.add("notificationPreferences");
            }
        } else {
            channels = notificationPreferences.get(subject.tenantId(), subject.profileId());
        }
        if (!changed.isEmpty()) {
            auditLogService.logForTenant(subject.tenantId(), AuditActions.PROFILE_UPDATED, "user_profile",
                    subject.profileId().toString(), null, "SUCCESS", Map.of("fields", String.join(",", changed)));
        }
        return toResponse(updated, channels);
    }

    private static ProfileSettingsResponse toResponse(SelfProfile profile, Channels channels) {
        return new ProfileSettingsResponse(profile.profileId(), profile.email(), profile.displayName(), profile.phoneE164(),
                profile.phoneVerifiedAt(), profile.preferredLanguage(),
                new ProfileSettingsResponse.NotificationPreferencesResponse(channels.push(), channels.sms(), channels.email()));
    }
}
