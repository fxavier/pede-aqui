package com.delivery.rating;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.common.PostgresTestContainerConfig;
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
 * V072 (LAC-28) on pre-existing data: aggregates are recomputed from the ratings already stored (seeded fake values
 * disappear), courier-only legacy rows move their stars to courier_stars, and fk_ratings_rater_user_id is validated
 * when every rater is a profile — but left NOT VALID (no data touched) when an orphan exists.
 */
class RatingsMigrationTest {

    private static String baseUrl;

    @BeforeAll
    static void container() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required");
        PostgresTestContainerConfig.POSTGRES.start();
        baseUrl = PostgresTestContainerConfig.POSTGRES.getJdbcUrl();
    }

    @Test
    void recomputesAggregatesAndValidatesTheRaterForeignKey() throws Exception {
        String url = freshDatabase("ratings_v072_clean");
        flyway(url, "67").migrate();
        UUID tenant;
        UUID vendor;
        UUID otherVendor;
        UUID courier;
        try (Connection c = DriverManager.getConnection(url, user(), password())) {
            tenant = tenant(c);
            vendor = vendor(c, tenant, 4.9);
            otherVendor = vendor(c, tenant, 3.7);
            courier = courier(c, tenant);
            UUID customer = profile(c, tenant);
            rating(c, tenant, order(c, tenant, vendor, customer), customer, vendor, null, 5);
            rating(c, tenant, order(c, tenant, vendor, customer), customer, vendor, null, 4);
            rating(c, tenant, order(c, tenant, vendor, customer), customer, vendor, null, 4);
            // Legacy courier-only row: its stars are the courier's.
            rating(c, tenant, order(c, tenant, vendor, customer), customer, null, courier, 2);
        }

        flyway(url, null).migrate();

        try (Connection c = DriverManager.getConnection(url, user(), password())) {
            assertAggregate(c, "vendors", vendor, 4.33, 3);
            assertAggregate(c, "vendors", otherVendor, 0.0, 0); // the seeded 3.7 was not a real average
            assertAggregate(c, "couriers", courier, 2.0, 1);
            assertThat(fkValidated(c)).isTrue();
            assertThat(scalarLong(c, "SELECT count(*) FROM ratings WHERE updated_at IS NULL")).isZero();
        }
    }

    @Test
    void leavesTheForeignKeyNotValidWhenARaterIsNotAProfile() throws Exception {
        String url = freshDatabase("ratings_v072_orphan");
        flyway(url, "67").migrate();
        try (Connection c = DriverManager.getConnection(url, user(), password())) {
            UUID tenant = tenant(c);
            UUID vendor = vendor(c, tenant, 0);
            UUID customer = profile(c, tenant);
            rating(c, tenant, order(c, tenant, vendor, customer), UUID.randomUUID(), vendor, null, 3);
        }

        flyway(url, null).migrate();

        try (Connection c = DriverManager.getConnection(url, user(), password())) {
            assertThat(fkValidated(c)).isFalse();
            assertThat(scalarLong(c, "SELECT count(*) FROM ratings")).isEqualTo(1); // nothing deleted
        }
    }

    // --- helpers ---

    private static void assertAggregate(Connection c, String table, UUID id, double average, int count) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT rating, rating_count FROM " + table + " WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getDouble(1)).isEqualTo(average);
                assertThat(rs.getInt(2)).isEqualTo(count);
            }
        }
    }

    private static boolean fkValidated(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT convalidated FROM pg_constraint WHERE conname = 'fk_ratings_rater_user_id'")) {
            assertThat(rs.next()).isTrue();
            return rs.getBoolean(1);
        }
    }

    private static long scalarLong(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static UUID tenant(Connection c) throws SQLException {
        UUID id = UUID.randomUUID();
        exec(c, "INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, 'T', ?, 'ACTIVE', 'MZN', now(), now())",
                id, "t-" + id);
        return id;
    }

    private static UUID vendor(Connection c, UUID tenant, double seededRating) throws SQLException {
        UUID id = UUID.randomUUID();
        exec(c, "INSERT INTO vendors (id, tenant_id, name, status, verification_status, rating, created_at, updated_at) VALUES (?, ?, 'V', 'ACTIVE', 'APPROVED', ?, now(), now())",
                id, tenant, seededRating);
        return id;
    }

    private static UUID profile(Connection c, UUID tenant) throws SQLException {
        UUID id = UUID.randomUUID();
        exec(c, "INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'P', 'ACTIVE', now(), now())",
                id, tenant, "kc-" + id, id + "@example.test");
        return id;
    }

    private static UUID courier(Connection c, UUID tenant) throws SQLException {
        UUID id = UUID.randomUUID();
        exec(c, "INSERT INTO couriers (id, tenant_id, user_profile_id, verification_status, available, rating, created_at, updated_at) VALUES (?, ?, ?, 'APPROVED', TRUE, 4.8, now(), now())",
                id, tenant, profile(c, tenant));
        return id;
    }

    private static UUID order(Connection c, UUID tenant, UUID vendor, UUID customer) throws SQLException {
        UUID id = UUID.randomUUID();
        exec(c, """
                INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, status, subtotal, fees, taxes, discounts,
                                    discount_total, total, checkout_idempotency_key, delivery_confirmation_code_hash, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'DELIVERED', 90, 10, 0, 0, 0, 100, ?, 'hash', now(), now())""",
                id, tenant, "R-" + id, customer, vendor, "k-" + id);
        return id;
    }

    private static void rating(Connection c, UUID tenant, UUID order, UUID rater, UUID vendor, UUID courier, int stars) throws SQLException {
        exec(c, "INSERT INTO ratings (id, tenant_id, order_id, rater_user_id, rated_vendor_id, rated_courier_id, stars, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), tenant, order, rater, vendor, courier, stars, Timestamp.from(Instant.now()));
    }

    private static void exec(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.executeUpdate();
        }
    }

    private static String freshDatabase(String name) throws SQLException {
        try (Connection admin = DriverManager.getConnection(baseUrl, user(), password()); Statement st = admin.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + name);
            st.execute("CREATE DATABASE " + name);
        }
        return baseUrl.replace("/delivery_test", "/" + name);
    }

    private static Flyway flyway(String url, String target) {
        var config = Flyway.configure().dataSource(url, user(), password()).locations("classpath:db/migration");
        if (target != null) config.target(target);
        return config.load();
    }

    private static String user() { return PostgresTestContainerConfig.POSTGRES.getUsername(); }

    private static String password() { return PostgresTestContainerConfig.POSTGRES.getPassword(); }
}
