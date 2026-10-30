package com.delivery.notification.api;

/** Notification delivery channels (LAC-48). IN_APP is the persisted inbox; the others go through external providers. */
public enum NotificationChannel {
    IN_APP, PUSH, SMS, EMAIL;

    /** True for the channels sent by the notification-delivery job through an external provider. */
    public boolean isExternal() {
        return this != IN_APP;
    }
}
