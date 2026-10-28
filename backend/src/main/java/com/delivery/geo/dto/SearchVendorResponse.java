package com.delivery.geo.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Vendor search result returned by the MVP discovery endpoint. deliveryFee comes from the same pricing engine as
 * checkout (INC-13, BigDecimal per ADR-0003); deliveryFeeBasis says whether it is a distance rule or an estimate
 * (POLICY_BASE / PLATFORM_DEFAULT when the customer's or vendor's position is unknown).
 */
public record SearchVendorResponse(
    UUID vendorId,
    String name,
    double distanceKm,
    Integer distanceMeters,
    boolean available,
    double rating,
    Integer estimatedDeliveryMinutes,
    BigDecimal deliveryFee,
    String deliveryFeeBasis
) {}
