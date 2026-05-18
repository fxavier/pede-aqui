package com.delivery.vendor.dto;

import jakarta.validation.constraints.Min;

/** Request used to update vendor availability settings. */
public record UpdateVendorAvailabilityRequest(boolean available, @Min(5) int estimatedDeliveryMinutes) {}
