package com.delivery.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UpdateCategoryRequest(
    @NotBlank String name,
    @NotNull String vertical,
    UUID parentId,
    @NotNull Boolean active
) {}