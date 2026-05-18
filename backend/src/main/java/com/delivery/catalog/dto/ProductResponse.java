package com.delivery.catalog.dto;

import java.util.List;
import java.util.UUID;

/** Product response returned by catalog endpoints. */
public record ProductResponse(
        UUID id,
        UUID vendorId,
        UUID categoryId,
        String name,
        String description,
        boolean requiresPrescriptionMetadata,
        boolean prohibitedFuel,
        List<SkuResponse> skus) {}
