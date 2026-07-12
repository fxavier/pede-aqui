package com.delivery.catalog.dto;

import java.util.UUID;

/** Public vendor profile for the customer storefront (anonymous browsing). */
public record VendorPublicResponse(
        UUID vendorId,
        String name,
        String description,
        String address,
        double rating,
        boolean available,
        Integer estimatedDeliveryMinutes,
        String logoUrl) {}
