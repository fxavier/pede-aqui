package com.delivery.geo.dto;

import jakarta.validation.constraints.NotBlank;

/** Request used by admins to create an operations zone. */
public record CreateZoneRequest(@NotBlank String name, String geometryWkt) {}
