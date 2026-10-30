package com.delivery.geo.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

/**
 * Normalised vendor search filters; also the "vendorSearch" cache key (LAC-51). Coordinates are rounded to 3
 * decimals (~100 m) so nearby customers share an entry, and the search itself runs on the rounded point so a
 * cached entry is exactly what a fresh search for its key returns. The page is deliberately not part of the key:
 * the full filtered list is cached once and every page (and the total count) is cut from it.
 */
public record VendorSearchQuery(UUID tenantId, Double latitude, Double longitude, int radiusMeters, String category,
                                Double minRating, Integer maxDeliveryMinutes, String sort) {
    static final int DEFAULT_RADIUS_METERS = 5000;
    static final String DEFAULT_SORT = "RELEVANCE";

    /** Builds a normalised query: rounded coordinates, default radius and upper-case sort. */
    public static VendorSearchQuery of(UUID tenantId, Double latitude, Double longitude, Integer radius, String category,
                                       Double minRating, Integer maxDeliveryMinutes, String sort) {
        return new VendorSearchQuery(tenantId, round(latitude), round(longitude),
                radius == null ? DEFAULT_RADIUS_METERS : radius, category, minRating, maxDeliveryMinutes,
                sort == null ? DEFAULT_SORT : sort.toUpperCase(Locale.ROOT));
    }

    /** Cache key with every filter; '*' marks the cross-tenant (anonymous/customer) browse scope. */
    public String cacheKey() {
        return String.join("|",
                tenantId == null ? "*" : tenantId.toString(),
                String.valueOf(latitude), String.valueOf(longitude), String.valueOf(radiusMeters),
                String.valueOf(category), String.valueOf(minRating), String.valueOf(maxDeliveryMinutes), sort);
    }

    private static Double round(Double coordinate) {
        return coordinate == null ? null
                : BigDecimal.valueOf(coordinate).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }
}
