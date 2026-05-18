package com.delivery.vendor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request used to update vendor profile information. */
public record UpdateVendorProfileRequest(@NotBlank String name, @NotNull UUID categoryId, Double latitude, Double longitude) {}
