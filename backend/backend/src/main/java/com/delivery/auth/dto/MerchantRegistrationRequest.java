package com.delivery.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request for merchant self-registration. */
public record MerchantRegistrationRequest(
        @Schema(example = "Acme Restaurant")
        @NotBlank String companyName,
        @Schema(example = "acme-restaurant")
        @NotBlank @Pattern(regexp = "[a-z0-9-]+") String companySlug,
        @Schema(example = "USD")
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String defaultCurrency,
        @Schema(example = "Acme Restaurant LLC")
        String legalName,
        @Schema(example = "12-3456789")
        String taxNumber,
        @Schema(example = "LLC")
        String businessType,
        @Schema(example = "Food & Beverage")
        String industry,
        @Schema(example = "United States")
        String country,
        @Schema(example = "New York")
        String city,
        @Schema(example = "123 Main St")
        String address,
        @Schema(example = "contact@acme-restaurant.com")
        @Email String companyEmail,
        @Schema(example = "+1-555-0123")
        String companyPhone,
        @Schema(example = "John")
        @NotBlank String firstName,
        @Schema(example = "Doe")
        @NotBlank String lastName,
        @Schema(example = "john@acme-restaurant.com")
        @NotBlank @Email String email,
        @Schema(example = "+1-555-0124")
        String phone,
        @Schema(example = "securePassword123")
        @NotBlank @Size(min = 8) String password,
        @Schema(example = "FRIEND2024")
        String referralCode,
        @Schema(example = "WELCOME10")
        String promoCode) {
}