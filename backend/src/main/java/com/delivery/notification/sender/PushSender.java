package com.delivery.notification.sender;

/**
 * Push provider seam (LAC-48): FCM for Android/iOS (APNs through FCM) and Web Push. Selected by
 * {@code app.notifications.push.provider}; only the {@code log} stub ships. A real provider is a new bean
 * implementing this interface, active for its provider id. Implementations must be thread-safe, must not throw for
 * provider errors (map them to a {@link SendResult}) and must never log the token or the body.
 */
public interface PushSender {
    /** Sends one push to one device token ({@code platform} = ANDROID|IOS|WEB). */
    SendResult send(String platform, String token, String title, String body, String templateKey);
}
