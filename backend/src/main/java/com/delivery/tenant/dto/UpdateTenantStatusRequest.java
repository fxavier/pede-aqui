package com.delivery.tenant.dto;

import jakarta.validation.constraints.NotBlank;

/** Request used to activate or deactivate a tenant. */
public record UpdateTenantStatusRequest(@NotBlank String status) {
}
