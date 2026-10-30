package com.delivery.notification.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the SUPPORT delivery log (LAC-48). Masked: the recipient appears only as {@code recipientMasked} (e.g.
 * {@code +25884*****67}, {@code ****abcd}); the rendered title/body are not exposed.
 */
public record NotificationDeliveryResponse(UUID id, String channel, String templateKey, int templateVersion, String status,
                                           int attempts, String lastError, UUID recipientProfileId, String recipientMasked,
                                           String businessReference, String providerMessageId, Instant createdAt,
                                           Instant nextAttemptAt, Instant sentAt) {}
