package com.delivery.rating.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Customer rating of a delivered order: store stars 1–5 (required), courier stars 1–5 (optional), comment ≤ 500. */
public record SubmitRatingRequest(
        @NotNull @Min(1) @Max(5) Integer vendorStars,
        @Min(1) @Max(5) Integer courierStars,
        @Size(max = 500) String comment) {}
