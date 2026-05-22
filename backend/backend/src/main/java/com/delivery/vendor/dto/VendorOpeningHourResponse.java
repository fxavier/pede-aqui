package com.delivery.vendor.dto;

import java.time.LocalTime;
import java.util.UUID;

/** Vendor opening-hour rule returned by API endpoints. */
public record VendorOpeningHourResponse(UUID id, UUID vendorId, int dayOfWeek, LocalTime opensAt, LocalTime closesAt, boolean closed) {}
