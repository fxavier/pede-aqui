package com.delivery.upload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Request to generate a presigned S3 URL for document uploads. */
public record CreateDocumentUploadUrlRequest(
        @NotBlank
        @Pattern(regexp = "(?i)^(application/pdf|image/(jpeg|jpg|png|webp))$", message = "Only PDF, JPEG, PNG, and WEBP documents are supported")
        String contentType,
        @NotBlank String purpose,
        @NotBlank String fileName) {}