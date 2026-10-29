package com.delivery.auth.dto;

import com.delivery.auth.validation.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request for customer self-registration. */
@PasswordPolicy
public record CustomerRegistrationRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Email String email,
        @NotBlank String password) implements PasswordPolicy.Credentials {
}
