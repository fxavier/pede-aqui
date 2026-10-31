package com.delivery.marketing;

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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

/**
 * V073 (LAC-29) data migration on a database at V072 holding legacy {@code coupons}/{@code promotions} rows of every
 * mapping category: migrated rows land in {@code promotion} with the documented mapping, unmappable ones are reported
 * SKIPPED with a reason, legacy rows are kept (only marked), pre-existing promotions are untouched and re-running the
 * migration function changes nothing.
 */
class LegacyPromotionMigrationTest {
    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant UNTIL = Instant.parse("2026-12-31T23:59:59Z");

    @BeforeAll
    static void container() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required");
        PostgresTestContainerConfig.POSTGRES.start();
    }

    @Test
    void migratesMappableRowsReportsTheRestAndIsIdempotent() throws Exception {
        String url = freshDatabase("legacy_promotions_v073");
        flyway(url, "72").migrate();
        UUID tenant;
        UUID vendor;
        UUID activePct;
        UUID inactiveFixed;
        UUID conflicting;
        UUID unknownType;
        UUID badPercentage;
        UUID badWindow;
        UUID automatic;
        UUID categoryScoped;
        UUID existingPromotion;
        try (Connection c = connection(url)) {
            tenant = tenant(c);
            vendor = vendor(c, tenant);
            existingPromotion = UUID.randomUUID();
            exec(c, """
                    INSERT INTO promotion (id, tenant_id, vendor_id, name, code, type, value, scope, starts_at, ends_at, status, created_at, updated_at)
                    VALUES (?, ?, ?, 'Existing', 'DUP', 'FIXED_AMOUNT', 2, 'ORDER', ?, ?, 'ACTIVE', now(), now())""",
                    existingPromotion, tenant, vendor, ts(FROM), ts(UNTIL));
            activePct = coupon(c, tenant, vendor, " summer15 ", "PERCENT", "15", "50.00", 100, 7, UNTIL, true);
            inactiveFixed = coupon(c, tenant, null, "FLAT5", "FIXED", "5", null, null, 0, null, false);
            conflicting = coupon(c, tenant, vendor, "dup", "PERCENTAGE", "10", null, null, 0, null, true);
            unknownType = coupon(c, tenant, vendor, "BOGO", "BUY_ONE_GET_ONE", "10", null, null, 0, null, true);
            badPercentage = coupon(c, tenant, vendor, "HUGE", "PERCENTAGE", "150", null, null, 0, null, true);
            badWindow = coupon(c, tenant, vendor, "BACKWARDS", "PERCENTAGE", "10", null, null, 0, Instant.parse("2026-08-01T00:00:00Z"), true);
            automatic = legacyPromotion(c, tenant, vendor, "Semana do pao", "ALL_ORDERS", "PERCENTAGE", "10", true);
            categoryScoped = legacyPromotion(c, tenant, vendor, "So categoria", "CATEGORY", "PERCENTAGE", "10", true);
        }

        flyway(url, null).migrate();

        try (Connection c = connection(url)) {
            assertThat(outcome(c, "coupons", activePct)).isEqualTo("MIGRATED:");
            Map<String, Object> pct = promotion(c, activePct);
            assertThat(pct).containsEntry("code", "SUMMER15").containsEntry("name", "Cupão SUMMER15")
                    .containsEntry("type", "PERCENTAGE").containsEntry("scope", "ORDER").containsEntry("status", "ACTIVE")
                    .containsEntry("usage_limit", 100).containsEntry("used_count", 7).containsEntry("vendor_id", vendor)
                    .containsEntry("starts_at", FROM).containsEntry("ends_at", UNTIL);
            assertThat((java.math.BigDecimal) pct.get("value")).isEqualByComparingTo("15");
            assertThat((java.math.BigDecimal) pct.get("min_order_total")).isEqualByComparingTo("50.00");

            assertThat(outcome(c, "coupons", inactiveFixed)).isEqualTo("MIGRATED:");
            assertThat(promotion(c, inactiveFixed)).containsEntry("type", "FIXED_AMOUNT").containsEntry("status", "PAUSED")
                    .containsEntry("ends_at", Instant.parse("2099-12-31T23:59:59Z"));

            assertThat(outcome(c, "coupons", conflicting)).isEqualTo("SKIPPED:code_conflict");
            assertThat(outcome(c, "coupons", unknownType)).isEqualTo("SKIPPED:unknown_discount_type");
            assertThat(outcome(c, "coupons", badPercentage)).isEqualTo("SKIPPED:invalid_value");
            assertThat(outcome(c, "coupons", badWindow)).isEqualTo("SKIPPED:invalid_window");

            assertThat(outcome(c, "promotions", automatic)).isEqualTo("MIGRATED:paused_for_review");
            assertThat(promotion(c, automatic)).containsEntry("code", null).containsEntry("status", "PAUSED")
                    .containsEntry("name", "Semana do pao").containsEntry("scope", "ORDER");
            assertThat(outcome(c, "promotions", categoryScoped)).isEqualTo("SKIPPED:unsupported_applies_to");

            // Legacy rows are kept; only migrated ones are marked (and point at their promotion).
            assertThat(count(c, "SELECT count(*) FROM coupons")).isEqualTo(6);
            assertThat(count(c, "SELECT count(*) FROM promotions")).isEqualTo(2);
            assertThat(count(c, "SELECT count(*) FROM coupons WHERE migrated_at IS NOT NULL AND migrated_to_promotion_id = id")).isEqualTo(2);
            assertThat(count(c, "SELECT count(*) FROM promotions WHERE migrated_at IS NOT NULL")).isEqualTo(1);
            // The pre-existing promotion is untouched.
            assertThat(promotion(c, existingPromotion)).containsEntry("code", "DUP").containsEntry("name", "Existing");
            assertThat(count(c, "SELECT count(*) FROM promotion")).isEqualTo(4);

            // Re-running migrates nothing and reports nothing.
            assertThat(count(c, "SELECT count(*) FROM marketing_migrate_legacy_promotions()")).isZero();
            assertThat(count(c, "SELECT count(*) FROM promotion")).isEqualTo(4);
            assertThat(count(c, "SELECT count(*) FROM legacy_promotion_migration")).isEqualTo(8);
        }
    }

    // --- helpers ---

    private static String outcome(Connection c, String table, UUID legacyId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT outcome, coalesce(reason, '') FROM legacy_promotion_migration WHERE legacy_table = ? AND legacy_id = ?")) {
            ps.setString(1, table);
            ps.setObject(2, legacyId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("mapping row for " + table + " " + legacyId).isTrue();
                return rs.getString(1) + ":" + rs.getString(2);
            }
        }
    }

    /** The promotion a legacy row was migrated to (same id when free), as column → value (timestamps as Instant). */
    private static Map<String, Object> promotion(Connection c, UUID legacyId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT p.* FROM promotion p
                 WHERE p.id = coalesce((SELECT m.promotion_id FROM legacy_promotion_migration m WHERE m.legacy_id = ?), ?)""")) {
            ps.setObject(1, legacyId);
            ps.setObject(2, legacyId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                    Object value = rs.getObject(i);
                    row.put(rs.getMetaData().getColumnName(i), value instanceof Timestamp t ? t.toInstant() : value);
                }
                return row;
            }
        }
    }

    private static long count(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static UUID coupon(Connection c, UUID tenant, UUID vendor, String code, String type, String value, String minOrder,
                               Integer maxUses, int usesCount, Instant validUntil, boolean active) throws SQLException {
        UUID id = UUID.randomUUID();
        exec(c, """
                INSERT INTO coupons (id, tenant_id, code, discount_type, discount_value, min_order_amount, max_uses, uses_count,
                                     vendor_id, valid_from, valid_until, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())""",
                id, tenant, code, type, new java.math.BigDecimal(value), minOrder == null ? null : new java.math.BigDecimal(minOrder),
                maxUses, usesCount, vendor, ts(FROM), validUntil == null ? null : ts(validUntil), active);
        return id;
    }

    private static UUID legacyPromotion(Connection c, UUID tenant, UUID vendor, String name, String appliesTo, String type,
                                        String value, boolean active) throws SQLException {
        UUID id = UUID.randomUUID();
        exec(c, """
                INSERT INTO promotions (id, tenant_id, name, description, discount_type, discount_value, vendor_id, applies_to,
                                        starts_at, ends_at, active, created_at, updated_at)
                VALUES (?, ?, ?, 'desc', ?, ?, ?, ?, ?, ?, ?, now(), now())""",
                id, tenant, name, type, new java.math.BigDecimal(value), vendor, appliesTo, ts(FROM), ts(UNTIL), active);
        return id;
    }

    private static UUID tenant(Connection c) throws SQLException {
        UUID id = UUID.randomUUID();
        exec(c, "INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, 'T', ?, 'ACTIVE', 'MZN', now(), now())",
                id, "t-" + id);
        return id;
    }

    private static UUID vendor(Connection c, UUID tenant) throws SQLException {
        UUID id = UUID.randomUUID();
        exec(c, "INSERT INTO vendors (id, tenant_id, name, status, verification_status, created_at, updated_at) VALUES (?, ?, 'V', 'ACTIVE', 'APPROVED', now(), now())",
                id, tenant);
        return id;
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    private static void exec(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.executeUpdate();
        }
    }

    private static Connection connection(String url) throws SQLException {
        return DriverManager.getConnection(url, PostgresTestContainerConfig.POSTGRES.getUsername(), PostgresTestContainerConfig.POSTGRES.getPassword());
    }

    private static String freshDatabase(String name) throws SQLException {
        try (Connection admin = connection(PostgresTestContainerConfig.POSTGRES.getJdbcUrl()); Statement st = admin.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + name);
            st.execute("CREATE DATABASE " + name);
        }
        return PostgresTestContainerConfig.POSTGRES.getJdbcUrl().replace("/delivery_test", "/" + name);
    }

    private static Flyway flyway(String url, String target) {
        var config = Flyway.configure()
                .dataSource(url, PostgresTestContainerConfig.POSTGRES.getUsername(), PostgresTestContainerConfig.POSTGRES.getPassword())
                .locations("classpath:db/migration");
        if (target != null) config.target(target);
        return config.load();
    }
}
