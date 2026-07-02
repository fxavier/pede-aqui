package com.delivery.catalog.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateVerticalRequest(@NotBlank String label, boolean active) {}
