package com.delivery.tenant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Request used by administrators to create a tenant. */
public record CreateTenantRequest(
        @Schema(example = "Local Tenant")
        @NotBlank String name,
        @Schema(example = "local")
        @NotBlank @Pattern(regexp = "[a-z0-9-]+") String slug,
        @Schema(example = "EUR")
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String defaultCurrency) {
}
