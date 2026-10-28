package com.delivery.customer.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create/replace payload for a customer address; latitude and longitude are optional but must come together. */
public record CustomerAddressRequest(
        @NotBlank @Size(max = 80) String label,
        @NotBlank @Size(max = 160) String recipientName,
        @Size(max = 40) String phone,
        @NotBlank @Size(max = 200) String line1,
        @Size(max = 200) String line2,
        @NotBlank @Size(max = 100) String city,
        @Size(max = 80) String state,
        @Size(max = 30) String postalCode,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Size(max = 500) String deliveryInstructions) {}
