package com.delivery.support.dto;

import com.delivery.support.entity.SupportTicketStatus;
import jakarta.validation.constraints.NotNull;

/** Request used by support/admin to change ticket lifecycle status. */
public record UpdateTicketStatusRequest(@NotNull SupportTicketStatus status) {}
