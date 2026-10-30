package com.delivery.notification.dto;

import java.time.Instant;

/** Effective notification template of a tenant; {@code source} = DEFAULT (platform) or TENANT (override). */
public record NotificationTemplateResponse(String templateKey, String channel, String locale, String title, String body,
                                           boolean active, int version, String source, Instant updatedAt) {}
