package com.delivery.catalog.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Partial product edit payload; null fields are left untouched. */
public record UpdateProductRequest(
        @Size(min = 1, max = 140) String name,
        @Size(max = 2000) String description,
        UUID categoryId,
        Boolean requiresPrescription) {}
