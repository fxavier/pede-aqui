package com.delivery.dispatch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * Outcome of a location batch (LAC-40): how many points were stored and how many were dropped, per reason
 * ({@code low_accuracy}, {@code too_old}, {@code future_timestamp}, {@code implausible_speed}, {@code duplicate}).
 */
public record CourierLocationBatchResponse(
        int accepted,
        int dropped,
        @Schema(description = "Dropped points per reason code") Map<String, Integer> droppedByReason) {}
