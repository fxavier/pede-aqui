package com.delivery.support.dto;

import com.delivery.support.entity.IncidentClassification;
import jakarta.validation.constraints.NotNull;

/** Request used by support/admin to classify ticket incidents. */
public record ClassifyTicketRequest(@NotNull IncidentClassification classification) {}
