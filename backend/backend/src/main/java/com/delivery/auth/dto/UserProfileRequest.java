package com.delivery.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request used to update editable user profile fields. */
public record UserProfileRequest(
        @NotBlank String displayName,
        @Email @NotBlank String email,
        String phone) {
}
