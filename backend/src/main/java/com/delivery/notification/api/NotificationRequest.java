package com.delivery.notification.api;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * One notification to dispatch (LAC-48): who receives it, which template key, the placeholder values and the channels
 * to try. A channel without an active template for the key is ignored, so callers may pass every channel.
 *
 * @param dedupKey stable key of the business fact (e.g. {@code order-status:<orderId>:ACCEPTED_BY_VENDOR}); the
 *                 per-channel idempotency key is {@code dedupKey + ":" + channel}, so a repeated dispatch is a no-op
 * @param params   placeholder values — identifiers only, never PINs/OTPs/secrets (sensitive names are dropped unless
 *                 the template explicitly allows them)
 */
public record NotificationRequest(UUID tenantId, UUID recipientProfileId, String recipientRole, String templateKey,
                                  String businessReference, Map<String, String> params, Set<NotificationChannel> channels,
                                  String dedupKey) {
    public NotificationRequest {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(recipientProfileId, "recipientProfileId");
        Objects.requireNonNull(templateKey, "templateKey");
        Objects.requireNonNull(dedupKey, "dedupKey");
        params = params == null ? Map.of() : Map.copyOf(params);
        channels = channels == null ? Set.of() : Set.copyOf(channels);
    }
}
