package com.delivery.catalog.dto;

import java.util.List;
import java.util.Map;
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
        Map<String, Object> attributes,
        String primaryImageKey,
        List<String> imageGallery,
        List<SkuResponse> skus) {}
