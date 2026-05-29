package com.delivery.vendor.mapper;

import com.delivery.vendor.dto.VendorResponse;
import com.delivery.vendor.dto.VendorDocumentResponse;
import com.delivery.vendor.dto.VendorOpeningHourResponse;
import com.delivery.vendor.entity.VendorDocument;
import com.delivery.vendor.entity.VendorOpeningHour;
import com.delivery.vendor.entity.Vendor;
import org.springframework.stereotype.Component;

/** Converts vendor entities to API DTOs. */
@Component
public class VendorMapper {
    public VendorResponse toResponse(Vendor vendor) {
        return new VendorResponse(
                vendor.getId(),
                vendor.getName(),
                vendor.getCategoryId(),
                vendor.getStatus(),
                vendor.getVerificationStatus(),
                vendor.getRating(),
                vendor.getEstimatedDeliveryMinutes(),
                vendor.isAvailable(),
                vendor.getLatitude(),
                vendor.getLongitude(),
                vendor.getOwnerName(),
                vendor.getNif(),
                vendor.getPhone(),
                vendor.getAddress(),
                vendor.getDescription(),
                vendor.getLogoStorageKey());
    }

    public VendorDocumentResponse toDocumentResponse(VendorDocument document) {
        return new VendorDocumentResponse(
                document.getId(),
                document.getVendorId(),
                document.getDocumentType(),
                document.getStorageKey(),
                document.getStatus());
    }

    public VendorOpeningHourResponse toOpeningHourResponse(VendorOpeningHour openingHour) {
        return new VendorOpeningHourResponse(
                openingHour.getId(),
                openingHour.getVendorId(),
                openingHour.getDayOfWeek(),
                openingHour.getOpensAt(),
                openingHour.getClosesAt(),
                openingHour.isClosed());
    }
}
