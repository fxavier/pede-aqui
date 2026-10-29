package com.delivery.geo;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.common.PostgresApiE2ETestBase;
import com.delivery.common.pricing.GeoPoint;
import com.delivery.common.security.TenancyProperties;
import com.delivery.geo.api.ServiceArea;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * W5-C (LAC-39): geo.api.ServiceArea evaluates ST_Covers over zones.area (derived from geometry_wkt by V062's trigger)
 * on PostGIS — inside/outside/vertex, per zone and any zone of the tenant, tenant isolation, and failing closed for
 * INACTIVE zones and zones whose WKT is not a valid polygon. Uses an area around Pemba that no other test seeds.
 */
class ServiceAreaPostgresE2ETest extends PostgresApiE2ETestBase {
    private static final String PEMBA = "POLYGON((40.40 -13.10, 40.60 -13.10, 40.60 -12.90, 40.40 -12.90, 40.40 -13.10))";
    private static final GeoPoint INSIDE = new GeoPoint(-12.97, 40.52);
    private static final GeoPoint OUTSIDE = new GeoPoint(-12.70, 40.52);
    private static final GeoPoint VERTEX = new GeoPoint(-13.10, 40.40);

    @Autowired private ServiceArea serviceArea;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenancyProperties tenancyProperties;

    private UUID tenant;
    private UUID pemba;

    @BeforeEach
    void seed() {
        tenant = tenancyProperties.getOperatorTenantId();
        // Zones of earlier test methods must not cover this test's points.
        jdbc.update("UPDATE zones SET status = 'INACTIVE' WHERE name LIKE 'W5C %'");
        pemba = zone("W5C Pemba", PEMBA, "ACTIVE");
    }

    @Test
    void coversPointsInsideAndOnTheBoundaryOfTheZone() {
        assertThat(serviceArea.covers(tenant, pemba, INSIDE)).isTrue();
        assertThat(serviceArea.covers(tenant, pemba, VERTEX)).isTrue();
        assertThat(serviceArea.covers(tenant, pemba, OUTSIDE)).isFalse();
        assertThat(serviceArea.covers(tenant, INSIDE)).isTrue();
        assertThat(serviceArea.covers(tenant, OUTSIDE)).isFalse();
    }

    @Test
    void anotherTenantOrAnotherZoneNeverCovers() {
        UUID elsewhere = zone("W5C Maputo", "POLYGON((32.50 -26.00, 32.70 -26.00, 32.70 -25.90, 32.50 -25.90, 32.50 -26.00))", "ACTIVE");

        assertThat(serviceArea.covers(UUID.randomUUID(), pemba, INSIDE)).isFalse();
        assertThat(serviceArea.covers(UUID.randomUUID(), INSIDE)).isFalse();
        assertThat(serviceArea.covers(tenant, elsewhere, INSIDE)).isFalse();
        assertThat(serviceArea.covers(tenant, UUID.randomUUID(), INSIDE)).isFalse();
        assertThat(serviceArea.covers(tenant, null, INSIDE)).isFalse();
        assertThat(serviceArea.covers(tenant, pemba, null)).isFalse();
    }

    @Test
    void inactiveZonesAndZonesWithoutAValidAreaFailClosed() {
        jdbc.update("UPDATE zones SET status = 'INACTIVE' WHERE id = ?", pemba);
        assertThat(serviceArea.covers(tenant, pemba, INSIDE)).isFalse();
        assertThat(serviceArea.covers(tenant, INSIDE)).isFalse();

        UUID point = zone("W5C ponto", "POINT(40.52 -12.97)", "ACTIVE");
        UUID bowtie = zone("W5C laço", "POLYGON((40.40 -13.10, 40.60 -12.90, 40.60 -13.10, 40.40 -12.90, 40.40 -13.10))", "ACTIVE");
        assertThat(serviceArea.covers(tenant, point, INSIDE)).isFalse();
        assertThat(serviceArea.covers(tenant, bowtie, INSIDE)).isFalse();
        assertThat(serviceArea.covers(tenant, INSIDE)).isFalse();
    }

    private UUID zone(String name, String wkt, String status) {
        Timestamp now = Timestamp.from(Instant.now());
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO zones (id, tenant_id, name, status, geometry_wkt, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, tenant, name, status, wkt, now, now);
        return id;
    }
}
