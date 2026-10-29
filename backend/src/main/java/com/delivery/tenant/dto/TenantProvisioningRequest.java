package com.delivery.tenant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Thin subscription lead used by the M2M tenant-provisioning endpoint. */
public record TenantProvisioningRequest(
        @Schema(example = "Restaurante Costa Nova")
        @NotBlank String companyName,
        @Schema(example = "Ana Machava")
        @NotBlank String contactName,
        @Schema(example = "ana@costanova.co.mz")
        @NotBlank @Email String email,
        @Schema(example = "+258-84-123-4567")
        @NotBlank String phone,
        @Schema(example = "Maputo")
        String city,
        @Schema(example = "pro-monthly")
        @NotBlank String planSlug,
        @Schema(example = "MONTHLY")
        @NotBlank String billingCycle,
        @Schema(example = "sub_9f2c1a")
        String externalRef) {
}
