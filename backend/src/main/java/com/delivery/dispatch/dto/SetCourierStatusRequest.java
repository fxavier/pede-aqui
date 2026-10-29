package com.delivery.dispatch.dto;

import com.delivery.dispatch.entity.CourierStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Courier going online (AVAILABLE) or offline (OFFLINE); BUSY and SUSPENDED are set by the system / OPS (LAC-42). */
public record SetCourierStatusRequest(
        @Schema(description = "AVAILABLE or OFFLINE; any other value is 400 invalid_courier_status") @NotNull CourierStatus status) {}
