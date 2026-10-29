package com.delivery.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;

/**
 * LAC-58 (COR-15): migrates to V049, seeds a realistic graph that touches every new foreign key (plus the columns
 * deliberately left without one), runs the V050 orphan report (the exact block from its header) and applies V051.
 * A second database seeds orphans to prove V050 still applies, the report names them and V051 refuses to validate.
 */
class ForeignKeyIntegrityMigrationTest {

    static final List<String> NEW_FOREIGN_KEYS = List.of(
            "fk_orders_customer_id", "fk_carts_customer_id", "fk_customer_addresses_customer_id",
            "fk_promotion_redemption_customer_id", "fk_orders_vendor_id", "fk_carts_vendor_id", "fk_products_vendor_id",
            "fk_inventory_items_vendor_id", "fk_commissions_vendor_id", "fk_coupons_vendor_id", "fk_promotions_vendor_id",
            "fk_promotion_vendor_id", "fk_orders_cart_id", "fk_promotion_target_product_id",
            "fk_promotion_target_category_id", "fk_vendors_category_id", "fk_deliveries_courier_id",
            "fk_cash_reconciliations_courier_id", "fk_couriers_operating_zone_id", "fk_verticals_tenant_id");

    @BeforeAll
    static void requiresDocker() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required");
        PostgresTestContainerConfig.POSTGRES.start();
    }

    @Test
    void realisticSeedHasNoOrphansAndEveryForeignKeyValidates() throws Exception {
        String url = freshDatabase("fk_integrity_clean");
        JdbcTemplate jdbc = jdbc(url);
        flyway(url, "49").migrate();
        Graph g = seedGraph(jdbc);
        // Columns deliberately without FK keep accepting their legitimate "dangling" values.
        jdbc.update("INSERT INTO customer_favorite_vendors (tenant_id, customer_id, vendor_id) VALUES (?, ?, ?)",
                g.tenant, UUID.randomUUID(), g.vendor); // Keycloak subject, not a profile id
        jdbc.update("INSERT INTO ratings (tenant_id, order_id, rater_user_id, rated_vendor_id, stars) VALUES (?, ?, ?, ?, 5)",
                g.tenant, g.order, UUID.randomUUID(), g.vendor);

        flyway(url, "50").migrate();
        assertThat(orphanReport(jdbc)).as("V050 orphan report on realistic data").isEmpty();
        assertThat(validated(jdbc).values()).as("NOT VALID after V050").hasSize(20).containsOnly(false);

        flyway(url, null).migrate();

        assertThat(validated(jdbc)).as("every LAC-58 FK exists and is validated").hasSize(20)
                .allSatisfy((name, valid) -> assertThat(valid).as(name).isTrue());
        assertThat(hasForeignKey(jdbc, "customer_favorite_vendors", "customer_id")).isFalse();
        assertThat(hasForeignKey(jdbc, "ratings", "rater_user_id")).isFalse();
        assertThat(hasForeignKey(jdbc, "orders", "delivery_address_id")).isFalse();
        assertThat(hasForeignKey(jdbc, "orders", "quote_delivery_fee_rule_id")).isFalse();

        // New writes with a dangling reference are rejected (23503 → DataIntegrityViolationException → 409 in the API).
        assertThatThrownBy(() -> insertOrder(jdbc, g.tenant, g.customer, UUID.randomUUID(), null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_orders_vendor_id");
        assertThatThrownBy(() -> jdbc.update("UPDATE couriers SET operating_zone_id = ? WHERE id = ?", UUID.randomUUID(), g.courier))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_couriers_operating_zone_id");
        // A parent still referenced cannot be deleted (NO ACTION).
        assertThatThrownBy(() -> jdbc.update("DELETE FROM vendors WHERE id = ?", g.vendor))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void orphansAreReportedAndBlockValidationWhileV050StillApplies() throws Exception {
        String url = freshDatabase("fk_integrity_orphans");
        JdbcTemplate jdbc = jdbc(url);
        flyway(url, "49").migrate();
        Graph g = seedGraph(jdbc);
        insertOrder(jdbc, g.tenant, UUID.randomUUID(), g.vendor, null); // customer without a profile
        jdbc.update("""
                INSERT INTO products (id, tenant_id, vendor_id, category_id, name, status, requires_prescription_metadata,
                                      manual_validation_required, prohibited_fuel, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Órfão', 'ACTIVE', FALSE, FALSE, FALSE, now(), now())""",
                UUID.randomUUID(), g.tenant, UUID.randomUUID(), g.category);

        flyway(url, "50").migrate();
        assertThat(orphanReport(jdbc)).containsExactly(Map.entry("fk_orders_customer_id", 1L), Map.entry("fk_products_vendor_id", 1L));

        assertThatThrownBy(() -> flyway(url, null).migrate()).isInstanceOf(FlywayException.class).hasMessageContaining("23503");
        // V051 rolled back as a whole: nothing validated, and the FKs keep guarding new writes.
        assertThat(validated(jdbc).values()).hasSize(20).containsOnly(false);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '51' AND success", Integer.class)).isZero();
        assertThatThrownBy(() -> insertOrder(jdbc, g.tenant, UUID.randomUUID(), g.vendor, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- seed ---------------------------------------------------------------------------------------------

    record Graph(UUID tenant, UUID vendor, UUID category, UUID customer, UUID courier, UUID order) {}

    /** One row per relationship covered by V050, wired the way the application writes them. */
    private static Graph seedGraph(JdbcTemplate jdbc) {
        Timestamp now = Timestamp.from(Instant.now());
        UUID tenant = id();
        jdbc.update("INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, 'FK', ?, 'ACTIVE', 'MZN', ?, ?)",
                tenant, "fk-" + tenant, now, now);
        UUID zone = id();
        jdbc.update("INSERT INTO zones (id, tenant_id, name, status, created_at, updated_at) VALUES (?, ?, 'Maputo', 'ACTIVE', ?, ?)", zone, tenant, now, now);
        UUID category = id();
        jdbc.update("INSERT INTO categories (id, tenant_id, name, slug, active, created_at, updated_at) VALUES (?, ?, 'Mercearia', ?, TRUE, ?, ?)",
                category, tenant, "cat-" + category, now, now);
        UUID vendor = id();
        jdbc.update("INSERT INTO vendors (id, tenant_id, name, category_id, status, verification_status, created_at, updated_at) VALUES (?, ?, 'Loja', ?, 'ACTIVE', 'APPROVED', ?, ?)",
                vendor, tenant, category, now, now);
        UUID customer = profile(jdbc, tenant, now);
        UUID courierProfile = profile(jdbc, tenant, now);
        UUID courier = id();
        jdbc.update("INSERT INTO couriers (id, tenant_id, user_profile_id, operating_zone_id, verification_status, available, created_at, updated_at) VALUES (?, ?, ?, ?, 'APPROVED', TRUE, ?, ?)",
                courier, tenant, courierProfile, zone, now, now);
        UUID product = id();
        jdbc.update("""
                INSERT INTO products (id, tenant_id, vendor_id, category_id, name, status, requires_prescription_metadata,
                                      manual_validation_required, prohibited_fuel, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Arroz', 'ACTIVE', FALSE, FALSE, FALSE, ?, ?)""", product, tenant, vendor, category, now, now);
        UUID sku = id();
        jdbc.update("INSERT INTO skus (id, tenant_id, product_id, sku_code, name, price, active, created_at, updated_at) VALUES (?, ?, ?, ?, 'Arroz 1kg', 18.00, TRUE, ?, ?)",
                sku, tenant, product, "sku-" + sku, now, now);
        jdbc.update("INSERT INTO inventory_items (id, tenant_id, vendor_id, sku_id, quantity_available, quantity_reserved, status, created_at, updated_at, version) VALUES (?, ?, ?, ?, 10, 0, 'ACTIVE', ?, ?, 0)",
                id(), tenant, vendor, sku, now, now);
        jdbc.update("""
                INSERT INTO customer_addresses (id, tenant_id, customer_id, label, recipient_name, line1, city, state, postal_code, created_at, updated_at)
                VALUES (?, ?, ?, 'Casa', 'Ana', 'Rua 1', 'Maputo', 'Maputo', '1100', ?, ?)""", id(), tenant, customer, now, now);
        UUID cart = id();
        jdbc.update("INSERT INTO carts (id, tenant_id, customer_id, vendor_id, status, subtotal, fees, taxes, discounts, total, created_at, updated_at) VALUES (?, ?, ?, ?, 'CHECKED_OUT', 18, 0, 0, 0, 18, ?, ?)",
                cart, tenant, customer, vendor, now, now);
        UUID order = insertOrder(jdbc, tenant, customer, vendor, cart);
        // Snapshot / historical ids without FK: the address and fee rule they came from may be gone.
        jdbc.update("UPDATE orders SET delivery_address_id = ?, quote_delivery_fee_rule_id = ? WHERE id = ?", id(), id(), order);
        UUID delivery = id();
        jdbc.update("INSERT INTO deliveries (id, tenant_id, order_id, courier_id, status, confirmation_code_hash, confirmation_attempts, created_at, updated_at) VALUES (?, ?, ?, ?, 'DELIVERED', 'hash', 0, ?, ?)",
                delivery, tenant, order, courier, now, now);
        jdbc.update("INSERT INTO cash_reconciliations (id, tenant_id, courier_id, delivery_id, order_id, amount, status, recorded_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 18, 'PENDING', ?, ?, ?)",
                id(), tenant, courier, delivery, order, now, now, now);
        jdbc.update("INSERT INTO commissions (id, tenant_id, order_id, vendor_id, basis_amount, commission_rate, commission_amount, status, created_at, updated_at) VALUES (?, ?, ?, ?, 18, 0.1, 1.8, 'PENDING', ?, ?)",
                id(), tenant, order, vendor, now, now);
        jdbc.update("INSERT INTO coupons (id, tenant_id, code, discount_type, discount_value, vendor_id, valid_from, created_at, updated_at) VALUES (?, ?, ?, 'FIXED', 5, ?, ?, ?, ?)",
                id(), tenant, "C-" + tenant, vendor, now, now, now);
        jdbc.update("INSERT INTO promotions (id, tenant_id, name, discount_type, discount_value, vendor_id, starts_at, created_at, updated_at) VALUES (?, ?, 'Legacy', 'FIXED', 5, ?, ?, ?, ?)",
                id(), tenant, vendor, now, now, now);
        Timestamp later = Timestamp.from(now.toInstant().plus(7, ChronoUnit.DAYS));
        UUID productPromotion = id();
        jdbc.update("INSERT INTO promotion (id, tenant_id, vendor_id, name, type, value, scope, target_product_id, starts_at, ends_at, status, created_at, updated_at) VALUES (?, ?, ?, 'Produto', 'PERCENTAGE', 10, 'PRODUCT', ?, ?, ?, 'ACTIVE', ?, ?)",
                productPromotion, tenant, vendor, product, now, later, now, now);
        jdbc.update("INSERT INTO promotion (id, tenant_id, name, type, value, scope, target_category_id, starts_at, ends_at, status, created_at, updated_at) VALUES (?, ?, 'Categoria', 'FIXED_AMOUNT', 5, 'CATEGORY', ?, ?, ?, 'ACTIVE', ?, ?)",
                id(), tenant, category, now, later, now, now);
        jdbc.update("INSERT INTO promotion_redemption (id, tenant_id, promotion_id, customer_id, order_id, amount, created_at) VALUES (?, ?, ?, ?, ?, 1.8, ?)",
                id(), tenant, productPromotion, customer, order, now);
        jdbc.update("INSERT INTO verticals (id, tenant_id, slug, label, created_at, updated_at) VALUES (?, ?, 'mercearia', 'Mercearia', ?, ?)",
                id(), tenant, now, now);
        return new Graph(tenant, vendor, category, customer, courier, order);
    }

    private static UUID insertOrder(JdbcTemplate jdbc, UUID tenant, UUID customer, UUID vendor, UUID cart) {
        UUID order = id();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, cart_id, status, subtotal, fees, taxes, discounts,
                                    discount_total, total, checkout_idempotency_key, delivery_confirmation_code_hash,
                                    delivery_confirmation_code_display, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'DELIVERED', 18, 0, 0, 0, 0, 18, ?, 'hash', '000000', ?, ?)""",
                order, tenant, "ref-" + order, customer, vendor, cart, "key-" + order, now, now);
        return order;
    }

    private static UUID profile(JdbcTemplate jdbc, UUID tenant, Timestamp now) {
        UUID id = id();
        jdbc.update("INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'User', 'ACTIVE', ?, ?)",
                id, tenant, "kc-" + id, id + "@example.test", now, now);
        return id;
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    /** Runs the orphan report exactly as written between the ORPHAN-REPORT markers of V050; returns fk → orphans. */
    private static Map<String, Long> orphanReport(JdbcTemplate jdbc) throws IOException {
        String sql;
        try (InputStream in = ForeignKeyIntegrityMigrationTest.class.getResourceAsStream("/db/migration/V050__foreign_keys_not_valid.sql")) {
            String migration = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String block = migration.substring(migration.indexOf("-- ORPHAN-REPORT-BEGIN"), migration.indexOf("-- ORPHAN-REPORT-END"));
            sql = block.lines().skip(1).map(line -> line.replaceFirst("^-- ?", "")).collect(Collectors.joining("\n"));
        }
        Map<String, Long> report = new LinkedHashMap<>();
        jdbc.query(sql, rs -> {
            report.put(rs.getString("fk"), rs.getLong("orphans"));
        });
        return report;
    }

    private static Map<String, Boolean> validated(JdbcTemplate jdbc) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        jdbc.query("SELECT conname, convalidated FROM pg_constraint WHERE contype = 'f' AND conname = ANY (?)",
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("text", NEW_FOREIGN_KEYS.toArray())),
                rs -> {
                    result.put(rs.getString(1), rs.getBoolean(2));
                });
        return result;
    }

    private static boolean hasForeignKey(JdbcTemplate jdbc, String table, String column) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_constraint c
                  JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
                 WHERE c.contype = 'f' AND c.conrelid = ?::regclass AND a.attname = ?""", Integer.class, table, column);
        return count != null && count > 0;
    }

    private static String freshDatabase(String name) throws SQLException {
        try (Connection admin = DriverManager.getConnection(PostgresTestContainerConfig.POSTGRES.getJdbcUrl(),
                PostgresTestContainerConfig.POSTGRES.getUsername(), PostgresTestContainerConfig.POSTGRES.getPassword());
             Statement st = admin.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + name);
            st.execute("CREATE DATABASE " + name);
        }
        return PostgresTestContainerConfig.POSTGRES.getJdbcUrl().replace("/delivery_test", "/" + name);
    }

    private static JdbcTemplate jdbc(String url) {
        // Unpooled: each statement opens and closes its own connection (keeps max_connections pressure at one).
        return new JdbcTemplate(new DriverManagerDataSource(url, PostgresTestContainerConfig.POSTGRES.getUsername(),
                PostgresTestContainerConfig.POSTGRES.getPassword()));
    }

    private static Flyway flyway(String url, String target) {
        var config = Flyway.configure()
                .dataSource(url, PostgresTestContainerConfig.POSTGRES.getUsername(), PostgresTestContainerConfig.POSTGRES.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    private static UUID id() {
        return UUID.randomUUID();
    }
}
