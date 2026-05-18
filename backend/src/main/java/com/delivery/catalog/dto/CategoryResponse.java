package com.delivery.catalog.dto;

import java.util.UUID;

/** Category data exposed through catalog APIs. */
public record CategoryResponse(UUID id, String name, String vertical, boolean active) {}
