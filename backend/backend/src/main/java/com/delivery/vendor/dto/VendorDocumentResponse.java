package com.delivery.vendor.dto;

import java.util.UUID;

/** Vendor document metadata returned by API endpoints. */
public record VendorDocumentResponse(UUID id, UUID vendorId, String documentType, String storageKey, String status) {}
