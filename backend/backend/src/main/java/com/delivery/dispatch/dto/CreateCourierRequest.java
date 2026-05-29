package com.delivery.dispatch.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CreateCourierRequest(
    @NotNull UUID userProfileId,
    UUID operatingZoneId,
    String fullName,
    String phone,
    String nif,
    String vehicleType,
    String vehiclePlate,
    LocalDate dateOfBirth
) {}