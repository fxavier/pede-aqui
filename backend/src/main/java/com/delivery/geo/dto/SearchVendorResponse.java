package com.delivery.geo.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Vendor search result returned by the MVP discovery endpoint. deliveryFee comes from the same pricing engine as
 * checkout (INC-13, BigDecimal per ADR-0003); deliveryFeeBasis says whether it is a distance rule or an estimate
 * (POLICY_BASE / PLATFORM_DEFAULT when the customer's or vendor's position is unknown).
 * <p>{@code open} and {@code nextOpeningAt} (LAC-27) are the store's state at request time: they are computed after the
 * cached result set is read (never cached), so the values stored in the "vendorSearch" cache for these two members
 * are meaningless placeholders.
 * <p>{@code distanceKm}/{@code distanceMeters} are the PostGIS geodesic distance from the searched point (LAC-39); both
 * are null when the request has no usable {@code lat}/{@code lng} (the radius filter then does not apply).
 * <p>{@code rating}/{@code ratingCount} (LAC-28) are the vendor's real rating aggregate (average, 0 without ratings);
 * cached with the result set, so a new rating may take up to the cache TTL (60 s) to show.
 */
public record SearchVendorResponse(
    UUID vendorId,
    String name,
    Double distanceKm,
    Integer distanceMeters,
    boolean available,
    double rating,
    int ratingCount,
    Integer estimatedDeliveryMinutes,
    BigDecimal deliveryFee,
    String deliveryFeeBasis,
    boolean open,
    Instant nextOpeningAt
) {
    /** Copy with the request-time open state (LAC-27). */
    public SearchVendorResponse withOpenState(boolean open, Instant nextOpeningAt) {
        return new SearchVendorResponse(vendorId, name, distanceKm, distanceMeters, available, rating, ratingCount,
                estimatedDeliveryMinutes, deliveryFee, deliveryFeeBasis, open, nextOpeningAt);
    }
}
