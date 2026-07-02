package com.delivery.geo.dto;

import java.util.UUID;

/** Operations zone response visible to admin and operations users. */
public record ZoneResponse(UUID id, String name, String status, String geometryWkt) {}
