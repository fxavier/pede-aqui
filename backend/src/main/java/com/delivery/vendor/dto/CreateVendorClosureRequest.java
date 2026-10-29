package com.delivery.vendor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Exceptional closure payload (LAC-27): the store is closed during [startsAt, endsAt). */
public record CreateVendorClosureRequest(
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotBlank @Size(max = 255) String reason) {}
