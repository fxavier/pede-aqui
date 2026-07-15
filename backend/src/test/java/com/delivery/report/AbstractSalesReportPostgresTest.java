package com.delivery.report;

import static org.mockito.Mockito.mock;

import com.delivery.common.PostgresTestContainerConfig;
import com.delivery.common.security.TenantContext;
import com.delivery.report.mapper.SalesReportMapper;
import com.delivery.report.repository.SalesReportRepository;
import com.delivery.report.service.SalesReportCsvExporter;
import com.delivery.report.service.SalesReportService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;

/**
 * Base for report tests that need real Postgres semantics (native date_trunc aggregation,
 * JDBC streaming). Runs on the shared Testcontainers Postgres and self-skips without Docker,
 * mirroring MigrationValidationTest. Seeds an immutable-order dataset with discounts,
 * refunds, commissions and category snapshots.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
abstract class AbstractSalesReportPostgresTest {

    private static final boolean DOCKER_AVAILABLE = DockerClientFactory.instance().isDockerAvailable();

    @BeforeAll
    static void requiresDockerOrExternalDatabase() {
        Assumptions.assumeTrue(DOCKER_AVAILABLE || System.getenv("SPRING_DATASOURCE_URL") != null,
                "Skipping sales report Postgres tests: no Docker environment and no SPRING_DATASOURCE_URL override");
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        if (!DOCKER_AVAILABLE) {
            return; // datasource comes from SPRING_DATASOURCE_* environment variables
        }
        PostgresTestContainerConfig.POSTGRES.start();
        registry.add("spring.datasource.url", PostgresTestContainerConfig.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresTestContainerConfig.POSTGRES::getUsername);
        registry.add("spring.datasource.password", PostgresTestContainerConfig.POSTGRES::getPassword);
    }

    // Reporting window covering the seeded orders.
    static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    static final Instant TO = Instant.parse("2026-06-03T00:00:00Z");

    static final Instant DAY_1 = Instant.parse("2026-06-01T10:00:00Z");
    static final Instant DAY_2 = Instant.parse("2026-06-02T10:00:00Z");

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected SalesReportRepository repository;
    protected SalesReportMapper mapper;
    protected SalesReportService service;
    protected SalesReportCsvExporter exporter;

    // Seeded identifiers (fresh per test; the test transaction rolls everything back).
    protected UUID tenant1;
    protected UUID tenant2;
    protected UUID vendor1;
    protected UUID vendor2;
    protected UUID vendor3;
    protected UUID category1;
    protected UUID category2;
    protected UUID product1;
    protected UUID product2;
    protected UUID sku1;
    protected UUID sku2;
    protected UUID order1;
    protected UUID order2;
    protected UUID order3;

    @BeforeEach
    void setUpReportStack() {
        repository = new SalesReportRepository(jdbcTemplate);
        mapper = new SalesReportMapper();
        service = new SalesReportService(repository, mapper, mock(TenantContext.class));
        exporter = new SalesReportCsvExporter(repository, mapper, service);
        seed();
    }

    /**
     * Dataset (tenant1 unless noted):
     * O1 vendor1 DELIVERED day1  — subtotal 200 (sku1 x2 @100, snapshot category1), fees 10,
     *    taxes 5, discount 15, total 200; refund 30 APPROVED; commission 20.
     * O2 vendor1 PAYMENT_CONFIRMED day2 — sku1 x1 @100, snapshot category NULL (fallback),
     *    total 100; refunds 50 REJECTED + 25 REQUESTED (not counted); commission 10.
     * O3 vendor2 CANCELLED (post-payment) day2 — sku2 x2 @50, snapshot category2, total 100;
     *    refund 100 REFUNDED; commission 10.
     * O4 vendor1 PAYMENT_PENDING day1 — total 999 (excluded: pre-payment).
     * O5 tenant2 vendor3 DELIVERED day1 — total 500 (excluded: other tenant).
     */
    private void seed() {
        tenant1 = UUID.randomUUID();
        tenant2 = UUID.randomUUID();
        insertTenant(tenant1, "Tenant One");
        insertTenant(tenant2, "Tenant Two");

        vendor1 = UUID.randomUUID();
        vendor2 = UUID.randomUUID();
        vendor3 = UUID.randomUUID();
        insertVendor(vendor1, tenant1, "Vendor One");
        insertVendor(vendor2, tenant1, "Vendor Two");
        insertVendor(vendor3, tenant2, "Vendor Three");

        category1 = UUID.randomUUID();
        category2 = UUID.randomUUID();
        insertCategory(category1, tenant1, "Mercearia");
        insertCategory(category2, tenant1, "Bebidas");

        product1 = UUID.randomUUID();
        product2 = UUID.randomUUID();
        insertProduct(product1, tenant1, vendor1, category1, "Arroz 1kg");
        insertProduct(product2, tenant1, vendor2, category2, "Sumo 1L");

        sku1 = UUID.randomUUID();
        sku2 = UUID.randomUUID();
        insertSku(sku1, tenant1, product1, "100.00");
        insertSku(sku2, tenant1, product2, "50.00");

        order1 = UUID.randomUUID();
        insertOrder(order1, tenant1, vendor1, "DELIVERED", DAY_1, "200.00", "10.00", "5.00", "15.00", "200.00");
        insertOrderItem(UUID.randomUUID(), order1, tenant1, sku1, "Arroz 1kg", "100.00", 2, "200.00", category1);
        UUID payment1 = UUID.randomUUID();
        insertPayment(payment1, tenant1, order1, "200.00");
        insertRefund(UUID.randomUUID(), tenant1, payment1, order1, "30.00", "APPROVED");
        insertCommission(UUID.randomUUID(), tenant1, order1, vendor1, "20.00");

        order2 = UUID.randomUUID();
        insertOrder(order2, tenant1, vendor1, "PAYMENT_CONFIRMED", DAY_2, "100.00", "0.00", "0.00", "0.00", "100.00");
        insertOrderItem(UUID.randomUUID(), order2, tenant1, sku1, "Arroz 1kg", "100.00", 1, "100.00", null);
        UUID payment2 = UUID.randomUUID();
        insertPayment(payment2, tenant1, order2, "100.00");
        insertRefund(UUID.randomUUID(), tenant1, payment2, order2, "50.00", "REJECTED");
        insertRefund(UUID.randomUUID(), tenant1, payment2, order2, "25.00", "REQUESTED");
        insertCommission(UUID.randomUUID(), tenant1, order2, vendor1, "10.00");

        order3 = UUID.randomUUID();
        insertOrder(order3, tenant1, vendor2, "CANCELLED", Instant.parse("2026-06-02T15:00:00Z"), "100.00", "0.00", "0.00", "0.00", "100.00");
        insertOrderItem(UUID.randomUUID(), order3, tenant1, sku2, "Sumo 1L", "50.00", 2, "100.00", category2);
        UUID payment3 = UUID.randomUUID();
        insertPayment(payment3, tenant1, order3, "100.00");
        insertRefund(UUID.randomUUID(), tenant1, payment3, order3, "100.00", "REFUNDED");
        insertCommission(UUID.randomUUID(), tenant1, order3, vendor2, "10.00");

        UUID order4 = UUID.randomUUID();
        insertOrder(order4, tenant1, vendor1, "PAYMENT_PENDING", Instant.parse("2026-06-01T09:00:00Z"), "999.00", "0.00", "0.00", "0.00", "999.00");
        insertOrderItem(UUID.randomUUID(), order4, tenant1, sku1, "Arroz 1kg", "111.00", 9, "999.00", category1);

        UUID order5 = UUID.randomUUID();
        insertOrder(order5, tenant2, vendor3, "DELIVERED", Instant.parse("2026-06-01T11:00:00Z"), "500.00", "0.00", "0.00", "0.00", "500.00");
    }

    protected void insertTenant(UUID id, String name) {
        jdbcTemplate.update(
                "INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', 'MZN', ?, ?)",
                id, name, "slug-" + id, now(), now());
    }

    protected void insertVendor(UUID id, UUID tenantId, String name) {
        jdbcTemplate.update(
                "INSERT INTO vendors (id, tenant_id, name, status, verification_status, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', 'APPROVED', ?, ?)",
                id, tenantId, name, now(), now());
    }

    protected void insertCategory(UUID id, UUID tenantId, String name) {
        jdbcTemplate.update(
                "INSERT INTO categories (id, tenant_id, name, slug, active, created_at, updated_at) VALUES (?, ?, ?, ?, TRUE, ?, ?)",
                id, tenantId, name, "slug-" + id, now(), now());
    }

    protected void insertProduct(UUID id, UUID tenantId, UUID vendorId, UUID categoryId, String name) {
        jdbcTemplate.update(
                """
                INSERT INTO products (id, tenant_id, vendor_id, category_id, name, status,
                                      requires_prescription_metadata, manual_validation_required, prohibited_fuel,
                                      created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', FALSE, FALSE, FALSE, ?, ?)""",
                id, tenantId, vendorId, categoryId, name, now(), now());
    }

    protected void insertSku(UUID id, UUID tenantId, UUID productId, String price) {
        jdbcTemplate.update(
                "INSERT INTO skus (id, tenant_id, product_id, sku_code, name, price, active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?::numeric, TRUE, ?, ?)",
                id, tenantId, productId, "sku-" + id, "SKU " + id, price, now(), now());
    }

    protected void insertOrder(UUID id, UUID tenantId, UUID vendorId, String status, Instant createdAt,
                               String subtotal, String fees, String taxes, String discountTotal, String total) {
        jdbcTemplate.update(
                """
                INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, status,
                                    subtotal, fees, taxes, discounts, discount_total, total,
                                    checkout_idempotency_key, delivery_confirmation_code_hash,
                                    delivery_confirmation_code_display, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::numeric, ?::numeric, ?::numeric, 0, ?::numeric, ?::numeric, ?, ?, '123456', ?, ?)""",
                id, tenantId, "ref-" + id, UUID.randomUUID(), vendorId, status,
                subtotal, fees, taxes, discountTotal, total, "key-" + id, "hash-" + id,
                Timestamp.from(createdAt), Timestamp.from(createdAt));
    }

    protected void insertOrderItem(UUID id, UUID orderId, UUID tenantId, UUID skuId, String productName,
                                   String unitPrice, int quantity, String lineTotal, UUID categorySnapshot) {
        jdbcTemplate.update(
                """
                INSERT INTO order_items (id, order_id, tenant_id, sku_id, product_name_snapshot, sku_name_snapshot,
                                         unit_price_snapshot, quantity, line_total, category_id_snapshot, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::numeric, ?, ?::numeric, ?, ?)""",
                id, orderId, tenantId, skuId, productName, productName, unitPrice, quantity, lineTotal, categorySnapshot, now());
    }

    protected void insertPayment(UUID id, UUID tenantId, UUID orderId, String amount) {
        jdbcTemplate.update(
                "INSERT INTO payments (id, tenant_id, order_id, amount, provider, idempotency_key, status, created_at, updated_at) VALUES (?, ?, ?, ?::numeric, 'MOCK', ?, 'CONFIRMED', ?, ?)",
                id, tenantId, orderId, amount, "pay-" + id, now(), now());
    }

    protected void insertRefund(UUID id, UUID tenantId, UUID paymentId, UUID orderId, String amount, String status) {
        jdbcTemplate.update(
                "INSERT INTO refunds (id, tenant_id, payment_id, order_id, amount, reason, status, idempotency_key, created_at, updated_at) VALUES (?, ?, ?, ?, ?::numeric, 'test', ?, ?, ?, ?)",
                id, tenantId, paymentId, orderId, amount, status, "refund-" + id, now(), now());
    }

    protected void insertCommission(UUID id, UUID tenantId, UUID orderId, UUID vendorId, String amount) {
        jdbcTemplate.update(
                "INSERT INTO commissions (id, tenant_id, order_id, vendor_id, basis_amount, commission_rate, commission_amount, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?::numeric, 0.10, ?::numeric, 'PENDING', ?, ?)",
                id, tenantId, orderId, vendorId, new BigDecimal(amount).multiply(BigDecimal.TEN).toPlainString(), amount, now(), now());
    }

    private static Timestamp now() {
        return Timestamp.from(Instant.parse("2026-06-01T00:00:00Z"));
    }
}
