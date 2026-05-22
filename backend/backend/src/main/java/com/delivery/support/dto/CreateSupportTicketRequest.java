package com.delivery.support.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/** Request used by customers to create support tickets. */
public record CreateSupportTicketRequest(UUID orderId, @NotBlank String subject, @NotBlank String description) {}
