package com.delivery.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request used to create a vendor product. */
public record CreateProductRequest(
    @NotNull UUID vendorId, 
    @NotNull UUID categoryId, 
    @NotBlank String name, 
    String description, 
    boolean requiresPrescriptionMetadata, 
    boolean prohibitedFuel,
    Map<String, Object> attributes,
    String primaryImageKey,
    List<String> imageGallery
) {}
