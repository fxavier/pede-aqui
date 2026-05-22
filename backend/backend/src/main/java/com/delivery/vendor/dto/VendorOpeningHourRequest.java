package com.delivery.vendor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalTime;

/** Opening-hour payload for one weekday. */
public record VendorOpeningHourRequest(
        @Min(1) @Max(7) int dayOfWeek,
        LocalTime opensAt,
        LocalTime closesAt,
        boolean closed) {}
