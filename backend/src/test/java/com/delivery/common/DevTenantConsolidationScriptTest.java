package com.delivery.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
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
 * ADR-0001 step 5: exercises the SQL core of {@code backend/scripts/adr-0001-consolidate-dev-tenants.sql} (the
 * PL/pgSQL block; psql meta-commands are the wrapper) on a throwaway database — dry-run changes nothing, apply moves
 * a single-vendor legacy tenant into the operator tenant and is idempotent, ambiguous tenants are skipped and a
 * UNIQUE collision aborts the run. This is a test database only; the script itself is still never run on a real one.
 */
class DevTenantConsolidationScriptTest {

    private static final String DATABASE = "dev_tenant_consolidation_test";
    private static final UUID OPERATOR = UUID.fromString("5e6d0c1a-7b2f-4f3e-9c8a-0d1e2f3a4b5c");
    private static final Path SCRIPT = Paths.get("scripts", "adr-0001-consolidate-dev-tenants.sql").toAbsolutePath().normalize();

    private static String jdbcUrl;
    private static String createLogTable;
    private static String consolidationBlock;

    @BeforeAll
    static void createDatabase() throws Exception {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required");
        Assumptions.assumeTrue(Files.exists(SCRIPT), "Script not found at " + SCRIPT);
        PostgresTestContainerConfig.POSTGRES.start();
        try (Connection admin = adminConnection(); Statement st = admin.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + DATABASE);
            st.execute("CREATE DATABASE " + DATABASE);
        }
        jdbcUrl = PostgresTestContainerConfig.POSTGRES.getJdbcUrl().replace("/delivery_test", "/" + DATABASE);
        Flyway.configure()
                .dataSource(jdbcUrl, PostgresTestContainerConfig.POSTGRES.getUsername(), PostgresTestContainerConfig.POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);
        int createIdx = script.indexOf("CREATE TABLE IF NOT EXISTS adr0001_consolidation_log");
        int doIdx = script.indexOf("DO $$", createIdx);
        int endIdx = script.indexOf("END $$;", doIdx) + "END $$;".length();
        createLogTable = script.substring(createIdx, doIdx);
        consolidationBlock = script.substring(doIdx, endIdx);
    }

    @Test
    void dryRunReportsAndChangesNothingThenApplyConsolidatesIdempotently() throws Exception {
        UUID legacy = UUID.randomUUID();
        UUID ambiguous = UUID.randomUUID();
        UUID vendor;
        UUID profile;
        UUID order;
        try (Connection c = connection()) {
            tenant(c, legacy, "legacy-single");
            tenant(c, ambiguous, "legacy-two-vendors");
            vendor = vendor(c, legacy);
            vendor(c, ambiguous);
            vendor(c, ambiguous);
            profile = merchantProfile(c, legacy, "VENDOR_ADMIN");
            merchantProfile(c, ambiguous, "VENDOR_ADMIN");
            order = catalogAndOrder(c, legacy, vendor, "legacy-cat-" + legacy);
        }

        // Dry-run: notices describe the move, nothing is persisted.
        List<String> notices = run("dry-run", legacy + "," + ambiguous, false);
        assertThat(notices).anyMatch(n -> n.contains("legacy-single") && n.contains("would move"));
        assertThat(notices).anyMatch(n -> n.contains("SKIP tenant legacy-two-vendors"));
        try (Connection c = connection()) {
            assertThat(tenantOf(c, "vendors", vendor)).isEqualTo(legacy);
            assertThat(count(c, "SELECT COUNT(*) FROM merchant_users WHERE app_user_profile_id = ?", profile)).isZero();
            assertThat(status(c, legacy)).isEqualTo("ACTIVE");
        }

        // Apply: the single-vendor tenant is folded in, the two-vendor tenant is left alone.
        notices = run("apply", legacy + "," + ambiguous, true);
        assertThat(notices).anyMatch(n -> n.contains("legacy-single") && n.contains("CONSOLIDATED (7 rows moved)"));
        assertThat(notices).anyMatch(n -> n.contains("SKIP tenant legacy-two-vendors"));
        try (Connection c = connection()) {
            assertThat(tenantOf(c, "vendors", vendor)).isEqualTo(OPERATOR);
            assertThat(tenantOf(c, "orders", order)).isEqualTo(OPERATOR);
            assertThat(tenantOf(c, "app_user_profiles", profile)).isEqualTo(OPERATOR);
            assertThat(count(c, "SELECT COUNT(*) FROM products WHERE tenant_id = ?", legacy)).isZero();
            assertThat(count(c, "SELECT COUNT(*) FROM merchant_users WHERE tenant_id = ? AND vendor_id = ? AND app_user_profile_id = ? AND role = 'OWNER'",
                    OPERATOR, vendor, profile)).isEqualTo(1);
            assertThat(status(c, legacy)).isEqualTo("CONSOLIDATED");
            assertThat(status(c, ambiguous)).isEqualTo("ACTIVE");
            assertThat(count(c, "SELECT COUNT(*) FROM vendors WHERE tenant_id = ?", ambiguous)).isEqualTo(2);
            assertThat(count(c, "SELECT COUNT(*) FROM adr0001_consolidation_log WHERE legacy_tenant_id = ? AND table_name = 'app_user_profiles'", legacy)).isEqualTo(1);
            // Audit history and the legacy fee policy (if any) are intentionally not rewritten.
            assertThat(count(c, "SELECT COUNT(*) FROM adr0001_consolidation_log WHERE table_name IN ('audit_logs','fee_policies','provisioning_requests')")).isZero();
        }

        // Second apply is a no-op: the consolidated tenant is not a candidate any more.
        long logRows;
        try (Connection c = connection()) {
            logRows = count(c, "SELECT COUNT(*) FROM adr0001_consolidation_log");
        }
        notices = run("apply", legacy + "," + ambiguous, true);
        assertThat(notices).noneMatch(n -> n.contains("legacy-single"));
        try (Connection c = connection()) {
            assertThat(count(c, "SELECT COUNT(*) FROM adr0001_consolidation_log")).isEqualTo(logRows);
        }
    }

    @Test
    void applyAbortsOnAUniqueCollisionWithoutMovingAnything() throws Exception {
        UUID legacy = UUID.randomUUID();
        UUID vendor;
        String slug = "shared-slug-" + legacy;
        try (Connection c = connection()) {
            tenant(c, legacy, "legacy-collision");
            vendor = vendor(c, legacy);
            merchantProfile(c, legacy, "VENDOR_STAFF");
            catalogAndOrder(c, legacy, vendor, slug);
            // Same category slug already present in the operator tenant → UNIQUE (tenant_id, slug) would break.
            category(c, OPERATOR, slug);
        }

        List<String> dryRun = run("dry-run", legacy.toString(), false);
        assertThat(dryRun).anyMatch(n -> n.contains("COLLISION") && n.contains("categories.slug"));

        assertThatThrownBy(() -> run("apply", legacy.toString(), true))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("collision");
        try (Connection c = connection()) {
            assertThat(tenantOf(c, "vendors", vendor)).isEqualTo(legacy);
            assertThat(status(c, legacy)).isEqualTo("ACTIVE");
        }
    }

    /** Runs the script's SQL core in one transaction; commits only when {@code commit} is true (the psql wrapper's \if). */
    private static List<String> run(String mode, String legacyTenants, boolean commit) throws SQLException {
        List<String> notices = new ArrayList<>();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('adr0001.operator', '" + OPERATOR + "', false)");
                st.execute("SELECT set_config('adr0001.legacy_tenants', '" + legacyTenants + "', false)");
                st.execute("SELECT set_config('adr0001.mode', '" + mode + "', false)");
                st.execute(createLogTable);
                st.execute(consolidationBlock);
                for (SQLWarning w = st.getWarnings(); w != null; w = w.getNextWarning()) {
                    notices.add(w.getMessage());
                }
                if (commit) {
                    c.commit();
                } else {
                    c.rollback();
                }
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
        return notices;
    }

    // --- seed helpers ---

    private static void tenant(Connection c, UUID id, String slug) throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());
        exec(c, "INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', 'MZN', ?, ?)",
                id, "Tenant " + slug, slug + "-" + id, now, now);
    }

    private static UUID vendor(Connection c, UUID tenantId) throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());
        UUID id = UUID.randomUUID();
        exec(c, "INSERT INTO vendors (id, tenant_id, name, status, verification_status, created_at, updated_at) VALUES (?, ?, 'Loja', 'ACTIVE', 'APPROVED', ?, ?)",
                id, tenantId, now, now);
        return id;
    }

    private static UUID merchantProfile(Connection c, UUID tenantId, String role) throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());
        UUID id = UUID.randomUUID();
        exec(c, "INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'Merchant', 'ACTIVE', ?, ?)",
                id, tenantId, "kc-" + id, id + "@example.test", now, now);
        exec(c, "INSERT INTO app_user_profile_roles (app_user_profile_id, role) VALUES (?, ?)", id, role);
        return id;
    }

    private static UUID category(Connection c, UUID tenantId, String slug) throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());
        UUID id = UUID.randomUUID();
        exec(c, "INSERT INTO categories (id, tenant_id, name, slug, active, created_at, updated_at) VALUES (?, ?, 'Cat', ?, TRUE, ?, ?)",
                id, tenantId, slug, now, now);
        return id;
    }

    private static UUID catalogAndOrder(Connection c, UUID tenantId, UUID vendorId, String categorySlug) throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());
        UUID categoryId = category(c, tenantId, categorySlug);
        UUID productId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        exec(c, "INSERT INTO products (id, tenant_id, vendor_id, category_id, name, status, requires_prescription_metadata, manual_validation_required, prohibited_fuel, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, 'Arroz', 'ACTIVE', FALSE, FALSE, FALSE, ?, ?)", productId, tenantId, vendorId, categoryId, now, now);
        exec(c, "INSERT INTO skus (id, tenant_id, product_id, sku_code, name, price, active, created_at, updated_at) VALUES (?, ?, ?, ?, 'Arroz 1kg', 18.00, TRUE, ?, ?)",
                skuId, tenantId, productId, "sku-" + skuId, now, now);
        exec(c, "INSERT INTO inventory_items (id, tenant_id, vendor_id, sku_id, quantity_available, quantity_reserved, status, created_at, updated_at, version) VALUES (?, ?, ?, ?, 5, 0, 'ACTIVE', ?, ?, 0)",
                UUID.randomUUID(), tenantId, vendorId, skuId, now, now);
        exec(c, "INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, status, subtotal, fees, taxes, discounts, discount_total, total, "
                + "checkout_idempotency_key, delivery_confirmation_code_hash, delivery_confirmation_code_display, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, 'DELIVERED', 90, 10, 0, 0, 0, 100, ?, ?, '000000', ?, ?)",
                orderId, tenantId, "ref-" + orderId, UUID.randomUUID(), vendorId, "key-" + orderId, "hash-" + orderId, now, now);
        return orderId;
    }

    private static UUID tenantOf(Connection c, String table, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT tenant_id FROM " + table + " WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as(table + " row " + id).isTrue();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    private static String status(Connection c, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT status FROM tenants WHERE id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
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
