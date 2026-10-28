package com.delivery.common.pricing;

import java.util.Optional;

/** A WGS84 point usable for straight-line distance; built only from complete, in-range, non-(0,0) coordinates. */
public record GeoPoint(double latitude, double longitude) {

    /** Empty when either coordinate is missing, out of range, or the (0,0) "not set" placeholder. */
    public static Optional<GeoPoint> of(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return Optional.empty();
        }
        if (latitude == 0.0 && longitude == 0.0) {
            return Optional.empty();
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            return Optional.empty();
        }
        return Optional.of(new GeoPoint(latitude, longitude));
    }
}
