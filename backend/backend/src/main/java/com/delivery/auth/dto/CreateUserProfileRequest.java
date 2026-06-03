package com.delivery.auth.dto;

import com.delivery.common.security.MarketplaceRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Set;

/** Request used to create a new user profile by admin. */
public record CreateUserProfileRequest(
        @NotBlank String keycloakUserId,
        @Email @NotBlank String email,
        @NotBlank String displayName,
        String fullName,
        String phone,
        String nif,
        LocalDate dateOfBirth,
        String address,
        @NotNull Set<MarketplaceRole> roles) {
}