package com.delivery.vendor.dto;

import jakarta.validation.constraints.NotBlank;

/** Request used to register vendor document metadata. */
public record CreateVendorDocumentRequest(@NotBlank String documentType, @NotBlank String storageKey) {}
