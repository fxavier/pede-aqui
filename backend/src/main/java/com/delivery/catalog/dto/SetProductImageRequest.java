package com.delivery.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Links a presigned-upload storage key as the product's primary image. */
public record SetProductImageRequest(
        @NotBlank @Size(max = 512) String storageKey) {}
