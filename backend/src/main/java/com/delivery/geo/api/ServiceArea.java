package com.delivery.geo.api;

import com.delivery.common.pricing.GeoPoint;
import java.util.UUID;

/**
 * Service-area check (LAC-39): whether a tenant's operations zone covers a WGS84 point, evaluated in PostGIS with
 * {@code ST_Covers(zones.area, point)} (boundary points are covered). Only ACTIVE zones with a valid area count; a zone
 * whose WKT is missing or invalid (zones.area NULL, see V062) covers nothing, so the check fails closed. Intended for
 * checkout v2 (W6-A), which validates the delivery address against the vendor's zone (vendors.delivery_zone_id);
 * deliberately not wired into checkout yet.
 */
public interface ServiceArea {

    /** True when the ACTIVE zone {@code zoneId} of {@code tenantId} covers {@code point}. */
    boolean covers(UUID tenantId, UUID zoneId, GeoPoint point);

    /** True when any ACTIVE zone of {@code tenantId} covers {@code point}. */
    boolean covers(UUID tenantId, GeoPoint point);
}
