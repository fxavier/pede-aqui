package com.delivery.catalog.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateVerticalRequest(@NotBlank String label) {}
