package com.delivery.dispatch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * One GPS fix sent by the courier app (LAC-40). Out-of-range values are 400 {@code validation_failed}; quality filters
 * (accuracy, age, implausible speed) drop the point instead and are reported in the batch response.
 */
public record CourierLocationPointRequest(
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @Schema(description = "Horizontal accuracy in metres (points above app.courier-location.max-accuracy-meters, default 100, are dropped)")
        @NotNull @DecimalMin("0") @DecimalMax("100000") Double accuracyMeters,
        @Schema(description = "Device-reported speed in m/s") @DecimalMin("0") @DecimalMax("100") Double speedMps,
        @Schema(description = "Heading in degrees, 0 (inclusive) to 360 (exclusive)") @DecimalMin("0") @DecimalMax(value = "360", inclusive = false) Double headingDegrees,
        @Schema(description = "Client timestamp of the fix (ISO-8601)") @NotNull Instant recordedAt) {}
