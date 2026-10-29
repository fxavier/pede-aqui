package com.delivery.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
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
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.DockerClientFactory;

/**
 * Migrates a fresh database to V041, seeds pre-INC-02/pre-INC-07 legacy orders covering every
 * category, then applies V042 (terminal PIN backfill) and V043 (legacy stock reconciliation) and
 * asserts the exact resulting PIN, stock_status, available and reserved values.
 */
class LegacyOrderDataFixMigrationTest {

    private static final String DATABASE = "legacy_data_fix_test";
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID VENDOR = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final String PIN = "123456";

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
    void backfillsPinAndReconcilesLegacyStockOnlyWhereIntended() throws Exception {
        flyway("41").migrate();
        UUID skuA;
        UUID skuB;
        UUID skuC;
        UUID skuD;
        UUID cancelledPrePickup;
        UUID deliveredA;
        UUID activeA;
        UUID rejected;
        UUID refundedNoCourier;
        UUID refundedCourierEngaged;
        UUID alreadyReleased;
        UUID failedAfterPickup;
        UUID onRoute;
        UUID deliveredD;
        UUID cancelledD;
        try (Connection c = connection()) {
            seedCatalog(c);
            skuA = sku(c, "A", 10, 9);
            skuB = sku(c, "B", 5, 3);
            skuC = sku(c, "C", 3, 2);
            skuD = sku(c, "D", 0, 2);
            // SKU A: 2 released, 3 committed, 4 stay reserved -> available 12, reserved 4
            cancelledPrePickup = order(c, "CANCELLED", "RESERVED", "DISPATCH_PENDING", skuA, 2);
            deliveredA = order(c, "DELIVERED", "RESERVED", "DELIVERED", skuA, 3);
            activeA = order(c, "PREPARING", "RESERVED", "DISPATCH_PENDING", skuA, 4);
            // SKU B: rejected + refunded (no courier) released; refunded with engaged courier kept -> available 7, reserved 1
            rejected = order(c, "REJECTED_BY_VENDOR", "RESERVED", "DISPATCH_PENDING", skuB, 1);
            refundedNoCourier = order(c, "REFUNDED", "RESERVED", "DISPATCH_PENDING", skuB, 1);
            refundedCourierEngaged = order(c, "REFUNDED", "RESERVED", "ACCEPTED", skuB, 1);
            // SKU C: already released order untouched; failed-after-pickup and on-route committed -> available 3, reserved 0
            alreadyReleased = order(c, "CANCELLED", "RELEASED", "DISPATCH_PENDING", skuC, 5);
            failedAfterPickup = order(c, "CANCELLED", "RESERVED", "FAILED_DELIVERY", skuC, 1);
            onRoute = order(c, "ASSIGNED_TO_COURIER", "RESERVED", "ON_ROUTE_TO_CUSTOMER", skuC, 1);
            // SKU D (hand-edited stock, reserved 2 < demand 4): commit 1 first, then release 1 -> available 1, reserved 0
            deliveredD = order(c, "DELIVERED", "RESERVED", "DELIVERED", skuD, 1);
            cancelledD = order(c, "CANCELLED", "RESERVED", "DISPATCH_PENDING", skuD, 3);
        }

        flyway(null).migrate();

        try (Connection c = connection()) {
            assertStock(c, skuA, 12, 4);
            assertStock(c, skuB, 7, 1);
            assertStock(c, skuC, 3, 0);
            assertStock(c, skuD, 1, 0);

            assertOrder(c, cancelledPrePickup, "RELEASED", null);
            assertOrder(c, deliveredA, "COMMITTED", null);
            assertOrder(c, activeA, "RESERVED", PIN);
            assertOrder(c, rejected, "RELEASED", null);
            assertOrder(c, refundedNoCourier, "RELEASED", null);
            assertOrder(c, refundedCourierEngaged, "RESERVED", PIN);
            assertOrder(c, alreadyReleased, "RELEASED", null);
            assertOrder(c, failedAfterPickup, "COMMITTED", null);
            assertOrder(c, onRoute, "COMMITTED", PIN);
            assertOrder(c, deliveredD, "COMMITTED", null);
            assertOrder(c, cancelledD, "RELEASED", null);

            // Re-running the reconciliation script is a no-op: it only touches orders still RESERVED that it would mark.
            String script = new String(new ClassPathResource("db/migration/V043__reconcile_legacy_order_stock.sql")
                    .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute(script);
            }
            c.commit();
            assertStock(c, skuA, 12, 4);
            assertStock(c, skuB, 7, 1);
            assertStock(c, skuC, 3, 0);
            assertStock(c, skuD, 1, 0);
            assertOrder(c, activeA, "RESERVED", PIN);
            assertOrder(c, refundedCourierEngaged, "RESERVED", PIN);
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

    private void seedCatalog(Connection c) throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());
        exec(c, "INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, 'Legacy', 'legacy', 'ACTIVE', 'MZN', ?, ?)", TENANT, now, now);
        // Real parents: V050/V051 (LAC-58) add and validate FKs from orders, products, inventory and deliveries.
        exec(c, "INSERT INTO vendors (id, tenant_id, name, status, verification_status, created_at, updated_at) VALUES (?, ?, 'Loja', 'ACTIVE', 'APPROVED', ?, ?)",
                VENDOR, TENANT, now, now);
        exec(c, "INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, 'kc-legacy-customer', 'customer@example.test', 'Customer', 'ACTIVE', ?, ?)",
                CUSTOMER, TENANT, now, now);
    }

    /** One courier per delivery: an active delivery per courier is UNIQUE (V035). */
    private UUID courier(Connection c) throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());
        UUID profile = UUID.randomUUID();
        UUID courier = UUID.randomUUID();
        exec(c, "INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'Courier', 'ACTIVE', ?, ?)",
                profile, TENANT, "kc-" + profile, profile + "@example.test", now, now);
        exec(c, "INSERT INTO couriers (id, tenant_id, user_profile_id, verification_status, available, created_at, updated_at) VALUES (?, ?, ?, 'APPROVED', TRUE, ?, ?)",
                courier, TENANT, profile, now, now);
        return courier;
    }

    private UUID sku(Connection c, String code, int available, int reserved) throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());
        UUID category = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        UUID sku = UUID.randomUUID();
        exec(c, "INSERT INTO categories (id, tenant_id, name, slug, active, created_at, updated_at) VALUES (?, ?, ?, ?, true, ?, ?)",
                category, TENANT, "Cat " + code, "cat-" + code.toLowerCase(), now, now);
        exec(c, "INSERT INTO products (id, tenant_id, vendor_id, category_id, name, status, requires_prescription_metadata, manual_validation_required, prohibited_fuel, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', false, false, false, ?, ?)", product, TENANT, VENDOR, category, "Product " + code, now, now);
        exec(c, "INSERT INTO skus (id, tenant_id, product_id, sku_code, name, price, active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 10.00, true, ?, ?)",
                sku, TENANT, product, "SKU-" + code, "Sku " + code, now, now);
        exec(c, "INSERT INTO inventory_items (id, tenant_id, vendor_id, sku_id, quantity_available, quantity_reserved, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)",
                UUID.randomUUID(), TENANT, VENDOR, sku, available, reserved, now, now);
        return sku;
    }

    private UUID order(Connection c, String status, String stockStatus, String deliveryStatus, UUID sku, int quantity) throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());
        UUID order = UUID.randomUUID();
        // Legacy rows: every pre-INC-02 order stored the plain PIN; V041 defaulted stock_status to RESERVED.
        exec(c, "INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, status, subtotal, fees, taxes, discounts, total, "
                + "checkout_idempotency_key, delivery_confirmation_code_hash, delivery_confirmation_code_display, created_at, updated_at, stock_status) "
                + "VALUES (?, ?, ?, ?, ?, ?, 10, 0, 0, 0, 10, ?, 'legacy-hash', ?, ?, ?, ?)",
                order, TENANT, "REF-" + order, CUSTOMER, VENDOR, status, order.toString(), PIN, now, now, stockStatus);
        exec(c, "INSERT INTO order_items (id, order_id, tenant_id, sku_id, product_name_snapshot, sku_name_snapshot, unit_price_snapshot, quantity, line_total, created_at) "
                + "VALUES (?, ?, ?, ?, 'P', 'S', 10, ?, ?, ?)", UUID.randomUUID(), order, TENANT, sku, quantity, 10 * quantity, now);
        exec(c, "INSERT INTO deliveries (id, tenant_id, order_id, courier_id, status, confirmation_code_hash, confirmation_attempts, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, 'legacy-hash', 0, ?, ?)", UUID.randomUUID(), TENANT, order, courier(c), deliveryStatus, now, now);
        return order;
    }

    private void assertStock(Connection c, UUID sku, int available, int reserved) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT quantity_available, quantity_reserved FROM inventory_items WHERE sku_id = ?")) {
            ps.setObject(1, sku);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).as("available").isEqualTo(available);
                assertThat(rs.getInt(2)).as("reserved").isEqualTo(reserved);
            }
        }
    }

    private void assertOrder(Connection c, UUID order, String stockStatus, String pin) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT stock_status, delivery_confirmation_code_display FROM orders WHERE id = ?")) {
            ps.setObject(1, order);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).as("stock_status").isEqualTo(stockStatus);
                assertThat(rs.getString(2)).as("PIN display").isEqualTo(pin);
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
