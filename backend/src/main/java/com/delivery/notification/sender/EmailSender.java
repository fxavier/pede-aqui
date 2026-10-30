package com.delivery.notification.sender;

/**
 * E-mail provider seam (LAC-48, e.g. SES). Selected by {@code app.notifications.email.provider}; only the {@code log}
 * stub ships. Implementations must not throw for provider errors and must never log the address or body.
 */
public interface EmailSender {
    /** Sends one plain-text e-mail. */
    SendResult send(String address, String subject, String body, String templateKey);
}
