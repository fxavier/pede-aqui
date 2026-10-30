package com.delivery.upload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;

/** Request to generate a presigned S3 URL for document uploads. */
public record CreateDocumentUploadUrlRequest(
        @NotBlank
        @Pattern(regexp = "(?i)^(application/pdf|image/(jpeg|jpg|png|webp))$", message = "Only PDF, JPEG, PNG, and WEBP documents are supported")
        String contentType,
        @NotBlank String purpose,
        @NotBlank String fileName,
        /** Exact file size in bytes; signed into the URL, so the storage rejects any other size (LAC-12). */
        @NotNull @Positive Long contentLength) {}