package com.delivery.vendor.dto;

import jakarta.validation.constraints.NotBlank;

/** Request used by admins to approve or reject vendor verification. */
public record VendorVerificationDecisionRequest(boolean approved, @NotBlank String reason) {}
