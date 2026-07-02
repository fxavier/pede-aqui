package com.delivery.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request for creating a new catalog category. */
public record CreateCategoryRequest(
    @NotBlank String name,
    @NotNull String vertical,
    UUID parentId
) {}