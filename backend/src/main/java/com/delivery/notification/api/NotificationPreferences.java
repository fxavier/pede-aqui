package com.delivery.notification.api;

import com.delivery.notification.entity.UserNotificationPreference;
import com.delivery.notification.repository.UserNotificationPreferenceRepository;
import com.delivery.notification.service.ChannelPreferences;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A user's notification channel opt-ins (LAC-48 preferences endpoint, W7-C) and the notification side of an account
 * erasure. Reads/writes {@code user_notification_preferences}; without a row the V018 defaults apply (push on, SMS
 * and e-mail off). The in-app inbox is not optional and has no switch.
 */
@Component
public class NotificationPreferences {
    /** Effective opt-ins per external channel. */
    public record Channels(boolean push, boolean sms, boolean email) {}

    /** Rows removed by {@link #erase}. */
    public record Erasure(int devices, int inboxNotifications, int preferences) {}

    private final UserNotificationPreferenceRepository repository;
    private final JdbcTemplate jdbc;

    public NotificationPreferences(UserNotificationPreferenceRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    /** The effective preferences of a profile (defaults when never saved). */
    @Transactional(readOnly = true)
    public Channels get(UUID tenantId, UUID profileId) {
        return toChannels(ChannelPreferences.of(repository.findByTenantIdAndUserProfileId(tenantId, profileId).orElse(null)));
    }

    /** Updates the given channels (null = unchanged), creating the row on first save; returns the effective result. */
    @Transactional
    public Channels update(UUID tenantId, UUID profileId, Boolean push, Boolean sms, Boolean email) {
        UserNotificationPreference row = repository.findByTenantIdAndUserProfileId(tenantId, profileId)
                .orElseGet(() -> new UserNotificationPreference(UUID.randomUUID(), tenantId, profileId));
        ChannelPreferences current = ChannelPreferences.of(row);
        row.updatePreferences(push != null ? push : current.push(), sms != null ? sms : current.sms(),
                email != null ? email : current.email());
        return toChannels(ChannelPreferences.of(repository.save(row)));
    }

    /**
     * Deletes the profile's push device tokens, in-app inbox and saved preferences (account erasure). The
     * {@code notification_deliveries} log keeps only masked recipients and is left to the retention cleaner.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Erasure erase(UUID tenantId, UUID profileId) {
        int devices = jdbc.update("DELETE FROM device_tokens WHERE user_profile_id = ?", profileId);
        int inbox = jdbc.update("DELETE FROM notifications WHERE tenant_id = ? AND recipient_user_id = ?", tenantId, profileId);
        int preferences = jdbc.update("DELETE FROM user_notification_preferences WHERE tenant_id = ? AND user_profile_id = ?",
                tenantId, profileId);
        return new Erasure(devices, inbox, preferences);
    }

    /** Number of push devices registered by the profile (for the export; the tokens themselves are never exported). */
    @Transactional(readOnly = true)
    public int deviceCount(UUID profileId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM device_tokens WHERE user_profile_id = ?", Integer.class, profileId);
        return count == null ? 0 : count;
    }

    private static Channels toChannels(ChannelPreferences preferences) {
        return new Channels(preferences.push(), preferences.sms(), preferences.email());
    }
}
