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
 * ADR-0001 step 2: migrates a fresh database to V048, seeds the pre-merchant_users world (one legacy tenant per
 * merchant, plus ambiguous tenants) and applies V049, asserting that the backfill associates profiles only where
 * the tenant has exactly one vendor and leaves ambiguous tenants untouched.
 */
class MerchantUsersMigrationTest {

    private static final String DATABASE = "merchant_users_migration_test";
    private static String jdbcUrl;

    @BeforeAll
    static void createDatabase() throws SQLException {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required");
        PostgresTestContainerConfig.POSTGRES.start();
        try (Connection admin = adminConnection(); Statement st = admin.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + DATABASE);
            st.execute("CREATE DATABASE " + DATABASE);
        }
        jdbcUrl = PostgresTestContainerConfig.POSTGRES.getJdbcUrl().replace("/delivery_test", "/" + DATABASE);
    }

    @Test
    void backfillsSingleVendorTenantsAndLeavesAmbiguousTenantsUntouched() throws Exception {
        flyway("48").migrate();

        UUID singleVendorTenant = UUID.randomUUID();
        UUID twoVendorTenant = UUID.randomUUID();
        UUID noVendorTenant = UUID.randomUUID();
        UUID vendor;
        UUID adminProfile;
        UUID staffProfile;
        UUID bothRolesProfile;
        UUID customerProfile;
        UUID ambiguousAdminProfile;
        UUID orphanAdminProfile;
        try (Connection c = connection()) {
            tenant(c, singleVendorTenant, "single");
            tenant(c, twoVendorTenant, "two");
            tenant(c, noVendorTenant, "none");
            vendor = vendor(c, singleVendorTenant);
            vendor(c, twoVendorTenant);
            vendor(c, twoVendorTenant);
            adminProfile = profile(c, singleVendorTenant, "VENDOR_ADMIN");
            staffProfile = profile(c, singleVendorTenant, "VENDOR_STAFF");
            bothRolesProfile = profile(c, singleVendorTenant, "VENDOR_STAFF", "VENDOR_ADMIN");
            customerProfile = profile(c, singleVendorTenant, "CUSTOMER");
            ambiguousAdminProfile = profile(c, twoVendorTenant, "VENDOR_ADMIN");
            orphanAdminProfile = profile(c, noVendorTenant, "VENDOR_ADMIN");
        }

        flyway(null).migrate();

        try (Connection c = connection()) {
            assertThat(association(c, adminProfile)).containsExactly(vendor + ":OWNER");
            assertThat(association(c, staffProfile)).containsExactly(vendor + ":STAFF");
            assertThat(association(c, bothRolesProfile)).containsExactly(vendor + ":OWNER");
            assertThat(association(c, customerProfile)).isEmpty();
            // Ambiguous tenants (0 or 2 vendors): never guessed.
            assertThat(association(c, ambiguousAdminProfile)).isEmpty();
            assertThat(association(c, orphanAdminProfile)).isEmpty();
            assertThat(count(c, "SELECT COUNT(*) FROM merchant_users WHERE tenant_id = ?", twoVendorTenant)).isZero();
            assertThat(count(c, "SELECT COUNT(*) FROM merchant_users WHERE tenant_id = ?", noVendorTenant)).isZero();
            assertThat(count(c, "SELECT COUNT(*) FROM merchant_users WHERE tenant_id = ?", singleVendorTenant)).isEqualTo(3);
            // Every backfilled row is consistent with its tenant.
            assertThat(count(c, "SELECT COUNT(*) FROM merchant_users mu JOIN vendors v ON v.id = mu.vendor_id WHERE v.tenant_id <> mu.tenant_id")).isZero();

            // The ambiguity report (header of V049) lists exactly the two untouched tenants.
            List<UUID> ambiguous = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT t.id FROM tenants t
                     WHERE (SELECT COUNT(*) FROM vendors v WHERE v.tenant_id = t.id) <> 1
                       AND EXISTS (SELECT 1 FROM app_user_profiles p JOIN app_user_profile_roles r ON r.app_user_profile_id = p.id
                                    WHERE p.tenant_id = t.id AND r.role IN ('VENDOR_ADMIN','VENDOR_STAFF'))"""); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ambiguous.add(rs.getObject(1, UUID.class));
                }
            }
            assertThat(ambiguous).containsExactlyInAnyOrder(twoVendorTenant, noVendorTenant);

            // Re-inserting the same association is rejected by the UNIQUE constraint (the guard relies on it).
            boolean duplicateRejected = false;
            try {
                exec(c, "INSERT INTO merchant_users (id, tenant_id, vendor_id, app_user_profile_id, role) VALUES (?, ?, ?, ?, 'STAFF')",
                        UUID.randomUUID(), singleVendorTenant, vendor, adminProfile);
            } catch (SQLException e) {
                duplicateRejected = "23505".equals(e.getSQLState());
            }
            assertThat(duplicateRejected).as("UNIQUE (tenant_id, app_user_profile_id, vendor_id)").isTrue();
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

    private static void tenant(Connection c, UUID id, String slug) throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());
        exec(c, "INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', 'MZN', ?, ?)",
                id, "Tenant " + slug, "t-" + slug + "-" + id, now, now);
    }

    private static UUID vendor(Connection c, UUID tenantId) throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());
        UUID id = UUID.randomUUID();
        exec(c, "INSERT INTO vendors (id, tenant_id, name, status, verification_status, created_at, updated_at) VALUES (?, ?, 'Loja', 'ACTIVE', 'PENDING', ?, ?)",
                id, tenantId, now, now);
        return id;
    }

    private static UUID profile(Connection c, UUID tenantId, String... roles) throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());
        UUID id = UUID.randomUUID();
        exec(c, "INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'User', 'ACTIVE', ?, ?)",
                id, tenantId, "kc-" + id, id + "@example.test", now, now);
        for (String role : roles) {
            exec(c, "INSERT INTO app_user_profile_roles (app_user_profile_id, role) VALUES (?, ?)", id, role);
        }
        return id;
    }

    private static List<String> association(Connection c, UUID profileId) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT vendor_id, role FROM merchant_users WHERE app_user_profile_id = ?")) {
            ps.setObject(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(rs.getObject(1, UUID.class) + ":" + rs.getString(2));
                }
            }
        }
        return rows;
    }

    private static long count(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
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

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, PostgresTestContainerConfig.POSTGRES.getUsername(), PostgresTestContainerConfig.POSTGRES.getPassword());
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(PostgresTestContainerConfig.POSTGRES.getJdbcUrl(),
                PostgresTestContainerConfig.POSTGRES.getUsername(), PostgresTestContainerConfig.POSTGRES.getPassword());
    }
}
