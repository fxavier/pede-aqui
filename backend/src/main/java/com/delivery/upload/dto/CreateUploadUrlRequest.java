package com.delivery.upload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Request to generate a presigned S3 URL for image uploads. */
public record CreateUploadUrlRequest(
        @NotBlank
        @Pattern(regexp = "(?i)^image/(jpeg|jpg|png|webp)$", message = "Only JPEG, PNG, and WEBP images are supported")
        String contentType,
        @NotBlank String purpose,
        @NotBlank String fileName) {}
