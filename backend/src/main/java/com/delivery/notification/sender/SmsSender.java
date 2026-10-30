package com.delivery.notification.sender;

/**
 * SMS provider seam of the backend (LAC-48). Not the Keycloak {@code sms-sender} SPI (W5-D), which sends login OTPs
 * inside Keycloak; the backend never sends OTPs. Selected by {@code app.notifications.sms.provider}; only the
 * {@code log} stub ships. Implementations must not throw for provider errors and must never log the number or body.
 */
public interface SmsSender {
    /** Sends one SMS to an E.164 number. */
    SendResult send(String phoneE164, String body, String templateKey);
}
