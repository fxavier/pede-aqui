package com.delivery.support.dto;

import com.delivery.support.entity.IncidentClassification;
import com.delivery.support.entity.SupportTicketStatus;
import java.time.Instant;
import java.util.UUID;

/** Support ticket read model with optional internal note visibility. */
public record SupportTicketResponse(
        UUID id,
        UUID orderId,
        String subject,
        String description,
        SupportTicketStatus status,
        IncidentClassification classification,
        String internalNote,
        String assigneeUserId,
        Instant createdAt) {}
