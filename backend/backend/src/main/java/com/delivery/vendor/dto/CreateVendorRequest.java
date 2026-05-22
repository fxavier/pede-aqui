package com.delivery.vendor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request used to register a new vendor profile. */
public record CreateVendorRequest(@NotBlank String name, @NotNull UUID categoryId, Double latitude, Double longitude) {}
