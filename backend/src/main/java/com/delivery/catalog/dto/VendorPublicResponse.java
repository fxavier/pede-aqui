package com.delivery.catalog.dto;

import java.util.UUID;

/** Public vendor profile for the customer storefront (anonymous browsing); rating = real average, ratingCount = number of ratings (LAC-28). */
public record VendorPublicResponse(
        UUID vendorId,
        String name,
        String description,
        String address,
        double rating,
        int ratingCount,
        boolean available,
        Integer estimatedDeliveryMinutes,
        String logoUrl) {}
