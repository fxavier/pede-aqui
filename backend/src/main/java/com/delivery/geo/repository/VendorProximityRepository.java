package com.delivery.geo.repository;

import java.sql.Types;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only PostGIS proximity query behind vendor search (LAC-39). Owned by geo (search is geo's use case) and reads
 * vendors.location / products directly in SQL, so geo needs no new dependency on the vendor module's repository types.
 */
@Repository
public class VendorProximityRepository {

    /**
     * Vendors whose {@code location} lies within {@code :radiusMeters} of the point ({@code ST_DWithin} on geography,
     * served by the GiST index idx_vendors_location) and that have at least one ACTIVE product, optionally restricted
     * to a product tenant and category (UUID text or NULL = no restriction), with the geodesic distance in km, nearest
     * first. Vendors without a location are never returned. Public so the query-plan test EXPLAINs the exact SQL.
     */
    public static final String WITHIN_RADIUS_SQL = """
            SELECT v.id AS vendor_id,
                   ST_Distance(v.location, CAST(ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326) AS geography)) / 1000.0 AS distance_km
              FROM vendors v
             WHERE ST_DWithin(v.location, CAST(ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326) AS geography), :radiusMeters)
               AND EXISTS (SELECT 1 FROM products p
                            WHERE p.vendor_id = v.id AND p.status = 'ACTIVE'
                              AND (CAST(:tenantId AS text) IS NULL OR p.tenant_id = CAST(:tenantId AS uuid))
                              AND (CAST(:categoryId AS text) IS NULL OR p.category_id = CAST(:categoryId AS uuid)))
             ORDER BY distance_km, v.id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public VendorProximityRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** See {@link #WITHIN_RADIUS_SQL}; {@code tenantId}/{@code categoryId} null means no restriction. */
    public List<VendorProximity> findWithinRadius(UUID tenantId, UUID categoryId, double latitude, double longitude, double radiusMeters) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId == null ? null : tenantId.toString(), Types.VARCHAR)
                .addValue("categoryId", categoryId == null ? null : categoryId.toString(), Types.VARCHAR)
                .addValue("latitude", latitude)
                .addValue("longitude", longitude)
                .addValue("radiusMeters", radiusMeters);
        return jdbc.query(WITHIN_RADIUS_SQL, params,
                (rs, row) -> new VendorProximity(rs.getObject("vendor_id", UUID.class), rs.getDouble("distance_km")));
    }

    /** One vendor within the radius and its geodesic distance in km. */
    public record VendorProximity(UUID vendorId, double distanceKm) {}
}
