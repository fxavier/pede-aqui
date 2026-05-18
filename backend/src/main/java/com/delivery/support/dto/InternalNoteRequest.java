package com.delivery.support.dto;

import jakarta.validation.constraints.NotBlank;

/** Request used by support/admin to store internal notes. */
public record InternalNoteRequest(@NotBlank String internalNote) {}
