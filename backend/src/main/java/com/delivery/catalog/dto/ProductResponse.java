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
        String status,
        boolean requiresPrescriptionMetadata,
        boolean prohibitedFuel,
        Map<String, Object> attributes,
        String primaryImageKey,
        String primaryImageUrl,
        List<String> imageGallery,
        List<SkuResponse> skus) {}
