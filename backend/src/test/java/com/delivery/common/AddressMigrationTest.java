package com.delivery.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

/**
 * INC-12 (AS-02, FR-003): migrates a fresh database to V045, seeds pre-INC-12 addresses and orders, then
 * applies V046 (geography backfill + sync trigger) and V047 (order address snapshot + immutability trigger)
 * and asserts the backfilled pins, the trigger behaviour and that legacy rows are left untouched.
 */
class AddressMigrationTest {

    private static final String DATABASE = "address_migration_test";
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID VENDOR = UUID.randomUUID();

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
    void backfillsGeographyKeepsItInSyncAndFreezesOrderSnapshots() throws Exception {
        flyway("45").migrate();
        UUID maputo;
        UUID noCoordinates;
        UUID halfCoordinates;
        UUID outOfRange;
        UUID legacyOrder;
        try (Connection c = connection()) {
            Timestamp now = Timestamp.from(Instant.now());
            exec(c, "INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, 'Addr', 'addr', 'ACTIVE', 'MZN', ?, ?)", TENANT, now, now);
            // Real parents: V050/V051 (LAC-58) add and validate FKs to app_user_profiles and vendors.
            exec(c, "INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) "
                    + "VALUES (?, ?, 'kc-addr-customer', 'addr@example.test', 'Ana', 'ACTIVE', ?, ?)", CUSTOMER, TENANT, now, now);
            exec(c, "INSERT INTO vendors (id, tenant_id, name, status, verification_status, created_at, updated_at) "
                    + "VALUES (?, ?, 'Loja', 'ACTIVE', 'APPROVED', ?, ?)", VENDOR, TENANT, now, now);
            maputo = address(c, -25.9692, 32.5732);
            noCoordinates = address(c, null, null);
            halfCoordinates = address(c, -25.9, null);
            outOfRange = address(c, 123.0, 32.5);
            legacyOrder = order(c);
        }

        flyway(null).migrate();

        try (Connection c = connection()) {
            assertPin(c, maputo, -25.9692, 32.5732);
            assertNoPin(c, noCoordinates);
            assertNoPin(c, halfCoordinates);
            assertNoPin(c, outOfRange); // reported by the validation query in V046, never guessed

            // Trigger keeps the pin in sync with latitude/longitude during the expand phase.
            UUID fresh = address(c, -19.8436, 34.8389);
            assertPin(c, fresh, -19.8436, 34.8389);
            exec(c, "UPDATE customer_addresses SET latitude = ?, longitude = ? WHERE id = ?", -25.0, 33.0, fresh);
            assertPin(c, fresh, -25.0, 33.0);
            exec(c, "UPDATE customer_addresses SET latitude = NULL, longitude = NULL WHERE id = ?", fresh);
            assertNoPin(c, fresh);
            // state/postal_code are optional now.
            exec(c, "UPDATE customer_addresses SET state = NULL, postal_code = NULL WHERE id = ?", fresh);

            // Legacy order: no snapshot, other updates unaffected.
            assertThat(scalar(c, "SELECT delivery_line1 FROM orders WHERE id = ?", legacyOrder)).isNull();
            exec(c, "UPDATE orders SET status = 'CANCELLED' WHERE id = ?", legacyOrder);

            // A recorded snapshot is immutable; the rest of the order is not.
            exec(c, "UPDATE orders SET delivery_address_id = ?, delivery_recipient_name = 'Ana', delivery_line1 = 'Av. Julius Nyerere 1200', "
                    + "delivery_city = 'Maputo', delivery_latitude = -25.96, delivery_longitude = 32.57 WHERE id = ?", maputo, legacyOrder);
            exec(c, "UPDATE orders SET status = 'DELIVERED' WHERE id = ?", legacyOrder);
            assertThatThrownBy(() -> exec(c, "UPDATE orders SET delivery_line1 = 'Outra rua' WHERE id = ?", legacyOrder))
                    .isInstanceOf(SQLException.class).hasMessageContaining("immutable");
            assertThatThrownBy(() -> exec(c, "UPDATE orders SET delivery_latitude = NULL WHERE id = ?", legacyOrder))
                    .isInstanceOf(SQLException.class).hasMessageContaining("immutable");
            assertThat(scalar(c, "SELECT delivery_line1 FROM orders WHERE id = ?", legacyOrder)).isEqualTo("Av. Julius Nyerere 1200");
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

    private static UUID address(Connection c, Double latitude, Double longitude) throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());
        UUID id = UUID.randomUUID();
        exec(c, "INSERT INTO customer_addresses (id, tenant_id, customer_id, label, recipient_name, line1, city, state, postal_code, latitude, longitude, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'Casa', 'Ana', 'Rua 1', 'Maputo', 'Maputo', '1100', ?, ?, ?, ?)", id, TENANT, CUSTOMER, latitude, longitude, now, now);
        return id;
    }

    private static UUID order(Connection c) throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());
        UUID order = UUID.randomUUID();
        exec(c, "INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, status, subtotal, fees, taxes, discounts, total, "
                + "checkout_idempotency_key, delivery_confirmation_code_hash, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, 'PREPARING', 10, 0, 0, 0, 10, ?, 'hash', ?, ?)",
                order, TENANT, "REF-" + order, CUSTOMER, VENDOR, order.toString(), now, now);
        return order;
    }

    private static void assertPin(Connection c, UUID id, double latitude, double longitude) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT ST_Y(location::geometry), ST_X(location::geometry), ST_SRID(location::geometry) FROM customer_addresses WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getDouble(1)).isEqualTo(latitude);
                assertThat(rs.getDouble(2)).isEqualTo(longitude);
                assertThat(rs.getInt(3)).isEqualTo(4326);
            }
        }
    }

    private static void assertNoPin(Connection c, UUID id) throws SQLException {
        assertThat(scalar(c, "SELECT location::text FROM customer_addresses WHERE id = ?", id)).isNull();
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
