package com.delivery.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.PostgresApiE2ETestBase;
import com.delivery.common.security.TenancyProperties;
import com.delivery.geo.repository.VendorProximityRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * W5-C (LAC-39) end-to-end on PostGIS: GET /search/vendors filters by radius with ST_DWithin on vendors.location
 * (exact boundary in metres), orders by geodesic distance, excludes vendors that cannot be placed when the customer
 * position is known, and lists every vendor with an unknown distance when it is not. Also captures the search query
 * plan and asserts the GiST index on vendors.location is usable for it. Each test seeds its own category, so the
 * shared database's other vendors never enter the results.
 */
class VendorGeoSearchPostgresE2ETest extends PostgresApiE2ETestBase {
    // Customer point already on the 3-decimal grid the search cache key rounds to.
    private static final double LAT = -25.950;
    private static final double LNG = 32.580;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenancyProperties tenancyProperties;

    private UUID tenant;
    private UUID categoryId;
    private UUID near;
    private UUID middle;
    private UUID far;
    private UUID noLocation;
    private UUID placeholder;

    @BeforeEach
    void seed() {
        tenant = tenancyProperties.getOperatorTenantId();
        categoryId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO categories (id, tenant_id, name, slug, active, created_at, updated_at) VALUES (?, ?, 'Geo', ?, TRUE, ?, ?)",
                categoryId, tenant, "geo-" + categoryId, now, now);
        near = vendor("Perto", -25.955, 32.580);      // ~0.55 km
        middle = vendor("Meio", -25.970, 32.585);     // ~2.3 km
        far = vendor("Longe", -26.000, 32.600);       // ~5.9 km
        noLocation = vendor("Sem pino", null, null);
        placeholder = vendor("Pino 0,0", 0.0, 0.0);
    }

    @Test
    void radiusFilterAndDistanceOrderComeFromPostgis() throws Exception {
        List<JsonNode> within5km = search(LAT, LNG, 5000, "DISTANCE");
        assertThat(ids(within5km)).containsExactly(near, middle);

        List<JsonNode> within10km = search(LAT, LNG, 10_000, "DISTANCE");
        assertThat(ids(within10km)).containsExactly(near, middle, far);
        double previous = 0;
        for (JsonNode row : within10km) {
            assertThat(row.get("distanceKm").asDouble()).isGreaterThan(previous);
            previous = row.get("distanceKm").asDouble();
        }
        assertThat(within10km.get(0).get("distanceKm").asDouble()).isBetween(0.50, 0.60);
        assertThat(within10km.get(2).get("distanceKm").asDouble()).isBetween(5.5, 6.5);
    }

    @Test
    void radiusBoundaryIsExactToTheMetre() throws Exception {
        double metres = jdbc.queryForObject(
                "SELECT ST_Distance(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography) FROM vendors WHERE id = ?",
                Double.class, LNG, LAT, middle);

        assertThat(ids(search(LAT, LNG, (int) Math.floor(metres), "DISTANCE"))).containsExactly(near);
        List<JsonNode> included = search(LAT, LNG, (int) Math.ceil(metres), "DISTANCE");
        assertThat(ids(included)).containsExactly(near, middle);
        // The reported distance is the same geodesic distance the filter used (km, 2 decimals; metres rounded).
        assertThat(included.get(1).get("distanceKm").asDouble()).isCloseTo(metres / 1000.0, within(0.0051));
        assertThat(included.get(1).get("distanceMeters").asInt()).isEqualTo((int) Math.round(metres));
        assertThat(search(LAT, LNG, 1, "DISTANCE")).isEmpty();
    }

    @Test
    void vendorsWithoutALocationAreExcludedWhenThePointIsKnownAndListedWithoutDistanceOtherwise() throws Exception {
        assertThat(ids(search(LAT, LNG, 20_000_000, "DISTANCE"))).doesNotContain(noLocation, placeholder);

        List<JsonNode> noPoint = search(null, null, 1, "DISTANCE"); // radius is meaningless without a point
        assertThat(ids(noPoint)).containsExactlyInAnyOrder(near, middle, far, noLocation, placeholder);
        assertThat(noPoint).allSatisfy(row -> {
            assertThat(row.get("distanceKm").isNull()).isTrue();
            assertThat(row.get("distanceMeters").isNull()).isTrue();
        });
    }

    @Test
    void movingAVendorMovesItInTheSearchThroughTheSyncTrigger() throws Exception {
        assertThat(ids(search(LAT, LNG, 1000, "DISTANCE"))).containsExactly(near);
        jdbc.update("UPDATE vendors SET latitude = ?, longitude = ? WHERE id = ?", -25.951, 32.580, far);
        assertThat(ids(search(LAT, LNG, 1000, "DISTANCE"))).containsExactly(far, near);
    }

    @Test
    void searchQueryCanUseTheGistIndexOnVendorLocation() {
        String sql = "EXPLAIN " + VendorProximityRepository.WITHIN_RADIUS_SQL
                .replace(":longitude", String.valueOf(LNG))
                .replace(":latitude", String.valueOf(LAT))
                .replace(":radiusMeters", "5000")
                .replace(":tenantId", "NULL")
                .replace(":categoryId", "'" + categoryId + "'");
        String defaultPlan = plan(sql, false);
        String indexPlan = plan(sql, true);
        System.out.println("W5-C search plan (planner defaults, test-sized table):\n" + defaultPlan);
        System.out.println("W5-C search plan (enable_seqscan=off):\n" + indexPlan);
        assertThat(indexPlan).contains("idx_vendors_location");
        assertThat(indexPlan).contains("&&"); // ST_DWithin's index condition (bounding-box overlap)

        String zonePlan = plan("EXPLAIN SELECT EXISTS (SELECT 1 FROM zones z WHERE z.tenant_id = '" + tenant + "' AND z.status = 'ACTIVE' "
                + "AND ST_Covers(z.area, CAST(ST_SetSRID(ST_MakePoint(32.6, -25.95), 4326) AS geography)))", true);
        System.out.println("W5-C service-area plan (enable_seqscan=off):\n" + zonePlan);
        assertThat(zonePlan).contains("idx_zones_area");
    }

    // ---- helpers -----------------------------------------------------------------------------------

    private String plan(String explainSql, boolean forceIndex) {
        return jdbc.execute((ConnectionCallback<String>) connection -> {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement st = connection.createStatement()) {
                if (forceIndex) {
                    st.execute("SET LOCAL enable_seqscan = off");
                    // W6-A: with the rows of the whole suite in the shared container, the planner may reach vendors
                    // through a full vendors_pkey index scan (or a pkey probe in a nested loop), which never shows the
                    // GiST index. Allowing only bitmap scans and no nested loops leaves the GiST index as the only
                    // non-seqscan access path that has a condition on vendors, independent of row statistics.
                    st.execute("SET LOCAL enable_nestloop = off");
                    st.execute("SET LOCAL enable_indexscan = off");
                }
                StringBuilder plan = new StringBuilder();
                try (ResultSet rs = st.executeQuery(explainSql)) {
                    while (rs.next()) {
                        plan.append(rs.getString(1)).append('\n');
                    }
                }
                return plan.toString();
            } finally {
                connection.rollback();
                connection.setAutoCommit(autoCommit);
            }
        });
    }

    private UUID vendor(String name, Double latitude, Double longitude) {
        Timestamp now = Timestamp.from(Instant.now());
        UUID vendorId = UUID.randomUUID();
        jdbc.update("INSERT INTO vendors (id, tenant_id, name, status, verification_status, available, latitude, longitude, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'ACTIVE', 'APPROVED', TRUE, ?, ?, ?, ?)", vendorId, tenant, name, latitude, longitude, now, now);
        jdbc.update("""
                INSERT INTO products (id, tenant_id, vendor_id, category_id, name, status,
                                      requires_prescription_metadata, manual_validation_required, prohibited_fuel, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Produto', 'ACTIVE', FALSE, FALSE, FALSE, ?, ?)""",
                UUID.randomUUID(), tenant, vendorId, categoryId, now, now);
        return vendorId;
    }

    private List<JsonNode> search(Double lat, Double lng, int radius, String sort) throws Exception {
        var request = get("/api/v1/search/vendors")
                .param("radius", String.valueOf(radius))
                .param("category", categoryId.toString())
                .param("sort", sort);
        if (lat != null) {
            request.param("lat", String.valueOf(lat)).param("lng", String.valueOf(lng));
        }
        String body = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<JsonNode> rows = new ArrayList<>();
        objectMapper.readTree(body).get("vendors").forEach(rows::add);
        return rows;
    }

    private static List<UUID> ids(List<JsonNode> rows) {
        return rows.stream().map(row -> UUID.fromString(row.get("vendorId").asText())).toList();
    }
}
