package com.delivery.geo.dto;

import java.util.UUID;

/** Vendor search result returned by the MVP discovery endpoint. */
public record SearchVendorResponse(UUID vendorId, String name, double distanceKm, boolean available, double rating) {}
