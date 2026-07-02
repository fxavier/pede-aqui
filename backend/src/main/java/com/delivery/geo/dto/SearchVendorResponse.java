package com.delivery.geo.dto;

import java.util.UUID;

/** Vendor search result returned by the MVP discovery endpoint. */
public record SearchVendorResponse(
    UUID vendorId, 
    String name, 
    double distanceKm, 
    Integer distanceMeters,
    boolean available, 
    double rating,
    Integer estimatedDeliveryMinutes,
    Double deliveryFee
) {}
