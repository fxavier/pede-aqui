package com.delivery.upload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;

/** Request to generate a presigned S3 URL for image uploads. */
public record CreateUploadUrlRequest(
        @NotBlank
        @Pattern(regexp = "(?i)^image/(jpeg|jpg|png|webp)$", message = "Only JPEG, PNG, and WEBP images are supported")
        String contentType,
        @NotBlank String purpose,
        @NotBlank String fileName,
        /** Exact file size in bytes; signed into the URL, so the storage rejects any other size (LAC-12). */
        @NotNull @Positive Long contentLength) {}
