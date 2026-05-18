package com.delivery.common.dto;

import java.time.Instant;
import java.util.UUID;

/** Read model for audit trail views in admin and operations consoles. */
public record AuditLogResponse(UUID id, String actorUserId, String action, String targetType, String targetId, String businessReference, String result, Instant createdAt) {}
