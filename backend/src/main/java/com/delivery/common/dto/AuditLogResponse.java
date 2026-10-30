package com.delivery.common.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Read model for audit trail views in admin and operations consoles. {@code ip}, {@code userAgent},
 * {@code correlationId} and {@code details} were added by LAC-15 and are null for entries written before V053.
 */
public record AuditLogResponse(UUID id, String actorUserId, String action, String targetType, String targetId, String businessReference, String result, Instant createdAt,
                               String ip, String userAgent, String correlationId, Map<String, Object> details) {}
