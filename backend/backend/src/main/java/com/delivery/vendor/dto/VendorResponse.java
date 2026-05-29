package com.delivery.vendor.dto;

import com.delivery.vendor.entity.VendorVerificationStatus;
import java.util.UUID;

/** Vendor data exposed to API clients. */
public record VendorResponse(
        UUID id,
        String name,
        UUID categoryId,
        String status,
        VendorVerificationStatus verificationStatus,
        double rating,
        int estimatedDeliveryMinutes,
        boolean available,
        Double latitude,
        Double longitude,
        String ownerName,
        String nif,
        String phone,
        String address,
        String description,
        String logoStorageKey) {}
