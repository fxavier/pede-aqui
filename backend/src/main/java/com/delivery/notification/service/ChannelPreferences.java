package com.delivery.notification.service;

import com.delivery.notification.api.NotificationChannel;
import com.delivery.notification.entity.UserNotificationPreference;

/**
 * A user's channel opt-ins (LAC-48), from {@code user_notification_preferences}. Without a row the V018 column
 * defaults apply: push on, SMS off, e-mail off. IN_APP is always on (the inbox is not optional).
 */
public record ChannelPreferences(boolean push, boolean sms, boolean email) {
    /** The V018 defaults for users who never saved preferences. */
    public static final ChannelPreferences DEFAULTS = new ChannelPreferences(true, false, false);

    /** Preferences of a stored row; null → {@link #DEFAULTS}. */
    public static ChannelPreferences of(UserNotificationPreference row) {
        if (row == null) {
            return DEFAULTS;
        }
        return new ChannelPreferences(Boolean.TRUE.equals(row.getPushEnabled()), Boolean.TRUE.equals(row.getSmsEnabled()),
                Boolean.TRUE.equals(row.getEmailEnabled()));
    }

    /** True when the user accepts notifications on this channel. */
    public boolean allows(NotificationChannel channel) {
        return switch (channel) {
            case IN_APP -> true;
            case PUSH -> push;
            case SMS -> sms;
            case EMAIL -> email;
        };
    }
}
