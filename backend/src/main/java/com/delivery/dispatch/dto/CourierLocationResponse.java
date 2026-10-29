package com.delivery.dispatch.dto;

import com.delivery.dispatch.entity.CourierStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Latest known position of an on-duty courier, for the OPS map and dispatch (LAC-40). */
public record CourierLocationResponse(
        UUID courierId,
        CourierStatus status,
        double latitude,
        double longitude,
        Double accuracyMeters,
        Double speedMps,
        Double headingDegrees,
        Instant recordedAt,
        Instant receivedAt,
        @Schema(description = "True when nothing was received within app.courier-location.presence-ttl (default 2 min)") boolean stale) {}
