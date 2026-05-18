package com.delivery.notification.dto;

import java.time.Instant;
import java.util.UUID;

/** Notification payload returned to the authenticated user. */
public record NotificationResponse(UUID id, String type, String title, String message, String businessReference, Instant readAt, Instant createdAt) {}
