package com.delivery.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

/**
 * W5-C (LAC-39): migrates a fresh database to V058, seeds vendors and zones with good and bad legacy geodata, then
 * applies V062 and asserts the backfill of vendors.location / zones.area (bad rows left NULL and listed by the report
 * queries, never guessed), and that the sync triggers keep both columns coherent with latitude/longitude and
 * geometry_wkt afterwards.
 */
class VendorZoneGeographyMigrationTest {

    private static final String DATABASE = "vendor_zone_geography_migration_test";
    private static final UUID TENANT = UUID.randomUUID();
    private static final String SQUARE = "POLYGON((32.50 -26.00, 32.70 -26.00, 32.70 -25.90, 32.50 -25.90, 32.50 -26.00))";

    private static String jdbcUrl;

    @BeforeAll
    static void createDatabase() throws SQLException {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required");
        PostgresTestContainerConfig.POSTGRES.start();
        try (Connection admin = DriverManager.getConnection(PostgresTestContainerConfig.POSTGRES.getJdbcUrl(),
                PostgresTestContainerConfig.POSTGRES.getUsername(), PostgresTestContainerConfig.POSTGRES.getPassword());
             Statement st = admin.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + DATABASE);
            st.execute("CREATE DATABASE " + DATABASE);
        }
        jdbcUrl = PostgresTestContainerConfig.POSTGRES.getJdbcUrl().replace("/delivery_test", "/" + DATABASE);
    }

    @Test
    void backfillsVendorLocationsAndZoneAreasAndKeepsThemInSync() throws Exception {
        flyway("58").migrate();
        UUID maputo;
        UUID noCoordinates;
        UUID halfCoordinates;
        UUID placeholder;
        UUID outOfRange;
        UUID square;
        UUID multi;
        UUID ewkt;
        UUID point;
        UUID bowtie;
        UUID garbage;
        UUID blank;
        UUID noWkt;
        UUID mercator;
        try (Connection c = connection()) {
            Timestamp now = Timestamp.from(Instant.now());
            exec(c, "INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, 'Geo', 'geo-w5c', 'ACTIVE', 'MZN', ?, ?)", TENANT, now, now);
            maputo = vendor(c, -25.9692, 32.5732);
            noCoordinates = vendor(c, null, null);
            halfCoordinates = vendor(c, -25.9, null);
            placeholder = vendor(c, 0.0, 0.0);
            outOfRange = vendor(c, 123.0, 32.5);

            square = zone(c, SQUARE);
            multi = zone(c, "MULTIPOLYGON(((32.50 -26.00, 32.70 -26.00, 32.70 -25.90, 32.50 -26.00)),((33.0 -25.0, 33.1 -25.0, 33.1 -24.9, 33.0 -25.0)))");
            ewkt = zone(c, "SRID=4326;" + SQUARE);
            point = zone(c, "POINT(32.58 -25.96)");
            bowtie = zone(c, "POLYGON((0 0, 1 1, 1 0, 0 1, 0 0))"); // self-intersecting: ST_IsValid = false
            garbage = zone(c, "not a geometry");
            blank = zone(c, "   ");
            noWkt = zone(c, null);
            mercator = zone(c, "SRID=3857;POLYGON((0 0, 10 0, 10 10, 0 10, 0 0))");
        }

        flyway(null).migrate();

        try (Connection c = connection()) {
            assertLocation(c, maputo, -25.9692, 32.5732);
            for (UUID unusable : List.of(noCoordinates, halfCoordinates, placeholder, outOfRange)) {
                assertNoLocation(c, unusable);
            }
            // The report query in V062 lists exactly the rows with unusable coordinates.
            assertThat(ids(c, """
                    SELECT id FROM vendors
                     WHERE (latitude IS NULL) <> (longitude IS NULL)
                        OR (latitude = 0 AND longitude = 0)
                        OR latitude NOT BETWEEN -90 AND 90 OR longitude NOT BETWEEN -180 AND 180"""))
                    .containsExactlyInAnyOrder(halfCoordinates, placeholder, outOfRange);

            for (UUID valid : List.of(square, multi, ewkt)) {
                assertThat(scalar(c, "SELECT GeometryType(area::geometry) FROM zones WHERE id = ?", valid)).isEqualTo("MULTIPOLYGON");
            }
            assertThat(scalar(c, "SELECT ST_SRID(area::geometry) FROM zones WHERE id = ?", square)).isEqualTo(4326);
            for (UUID invalid : List.of(point, bowtie, garbage, blank, noWkt, mercator)) {
                assertThat(scalar(c, "SELECT area::text FROM zones WHERE id = ?", invalid)).isNull();
            }
            assertThat(ids(c, """
                    SELECT id FROM zones
                     WHERE geometry_wkt IS NOT NULL AND btrim(geometry_wkt) <> '' AND area IS NULL"""))
                    .containsExactlyInAnyOrder(point, bowtie, garbage, mercator);

            // Vendor trigger: insert/update keep location in sync; unusable coordinates clear it, never fail.
            UUID beira = vendor(c, -19.8436, 34.8389);
            assertLocation(c, beira, -19.8436, 34.8389);
            exec(c, "UPDATE vendors SET latitude = ?, longitude = ? WHERE id = ?", -25.0, 33.0, beira);
            assertLocation(c, beira, -25.0, 33.0);
            exec(c, "UPDATE vendors SET latitude = 0, longitude = 0 WHERE id = ?", beira);
            assertNoLocation(c, beira);
            exec(c, "UPDATE vendors SET latitude = 95, longitude = 33 WHERE id = ?", beira);
            assertNoLocation(c, beira);
            exec(c, "UPDATE vendors SET latitude = -25.0, longitude = 33.0 WHERE id = ?", beira);
            exec(c, "UPDATE vendors SET name = 'Outro nome' WHERE id = ?", beira); // untouched columns keep the location
            assertLocation(c, beira, -25.0, 33.0);

            // Zone trigger: area follows geometry_wkt; a bad WKT clears it instead of failing the write.
            UUID fresh = zone(c, SQUARE);
            assertThat(scalar(c, "SELECT ST_Covers(area, ST_SetSRID(ST_MakePoint(32.60, -25.95), 4326)::geography) FROM zones WHERE id = ?", fresh)).isEqualTo(true);
            assertThat(scalar(c, "SELECT ST_Covers(area, ST_SetSRID(ST_MakePoint(32.60, -25.80), 4326)::geography) FROM zones WHERE id = ?", fresh)).isEqualTo(false);
            exec(c, "UPDATE zones SET geometry_wkt = 'POLYGON((' WHERE id = ?", fresh);
            assertThat(scalar(c, "SELECT area::text FROM zones WHERE id = ?", fresh)).isNull();
            exec(c, "UPDATE zones SET geometry_wkt = ? WHERE id = ?", SQUARE, fresh);
            assertThat(scalar(c, "SELECT area IS NOT NULL FROM zones WHERE id = ?", fresh)).isEqualTo(true);

            // GiST indexes exist on both new columns.
            assertThat(scalar(c, "SELECT indexdef FROM pg_indexes WHERE indexname = ?", "idx_vendors_location").toString()).contains("gist (location)");
            assertThat(scalar(c, "SELECT indexdef FROM pg_indexes WHERE indexname = ?", "idx_zones_area").toString()).contains("gist (area)");
        }
    }

    private static Flyway flyway(String target) {
        var config = Flyway.configure()
                .dataSource(jdbcUrl, PostgresTestContainerConfig.POSTGRES.getUsername(), PostgresTestContainerConfig.POSTGRES.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, PostgresTestContainerConfig.POSTGRES.getUsername(), PostgresTestContainerConfig.POSTGRES.getPassword());
    }

    private static UUID vendor(Connection c, Double latitude, Double longitude) throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());
        UUID id = UUID.randomUUID();
        exec(c, "INSERT INTO vendors (id, tenant_id, name, status, verification_status, latitude, longitude, created_at, updated_at) "
                + "VALUES (?, ?, 'Loja', 'ACTIVE', 'APPROVED', ?, ?, ?, ?)", id, TENANT, latitude, longitude, now, now);
        return id;
    }

    private static UUID zone(Connection c, String wkt) throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());
        UUID id = UUID.randomUUID();
        exec(c, "INSERT INTO zones (id, tenant_id, name, status, geometry_wkt, created_at, updated_at) VALUES (?, ?, 'Zona', 'ACTIVE', ?, ?, ?)",
                id, TENANT, wkt, now, now);
        return id;
    }

    private static void assertLocation(Connection c, UUID id, double latitude, double longitude) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT ST_Y(location::geometry), ST_X(location::geometry), ST_SRID(location::geometry) FROM vendors WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getDouble(1)).isEqualTo(latitude);
                assertThat(rs.getDouble(2)).isEqualTo(longitude);
                assertThat(rs.getInt(3)).isEqualTo(4326);
            }
        }
    }

    private static void assertNoLocation(Connection c, UUID id) throws SQLException {
        assertThat(scalar(c, "SELECT location::text FROM vendors WHERE id = ?", id)).isNull();
    }

    private static List<UUID> ids(Connection c, String sql) throws SQLException {
        List<UUID> ids = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add((UUID) rs.getObject(1));
            }
        }
        return ids;
    }

    private static Object scalar(Connection c, String sql, Object arg) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, arg);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getObject(1);
            }
        }
    }

    private static void exec(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            ps.executeUpdate();
        }
    }
}
