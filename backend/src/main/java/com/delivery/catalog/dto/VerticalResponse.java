package com.delivery.catalog.dto;

import java.util.UUID;

/** Vertical data exposed to API clients. */
public record VerticalResponse(UUID id, String slug, String label, boolean active) {}
