package com.delivery.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request used to create a vendor product. */
public record CreateProductRequest(@NotNull UUID vendorId, @NotNull UUID categoryId, @NotBlank String name, String description, boolean requiresPrescriptionMetadata, boolean prohibitedFuel) {}
