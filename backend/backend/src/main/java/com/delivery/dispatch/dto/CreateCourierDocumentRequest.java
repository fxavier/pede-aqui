package com.delivery.dispatch.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCourierDocumentRequest(
    @NotBlank String documentType,
    @NotBlank String storageKey
) {}