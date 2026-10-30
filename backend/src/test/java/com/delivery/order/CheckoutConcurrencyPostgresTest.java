package com.delivery.order;

import com.delivery.upload.service.StoredUploadValidator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.when;

import com.delivery.cart.mapper.CartMapper;
import com.delivery.cart.service.CartService;
import com.delivery.cart.service.PricingService;
import com.delivery.catalog.service.CatalogAccessGuard;
import com.delivery.common.PostgresTestContainerConfig;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.delivery.entity.DeliveryStatus;
import com.delivery.delivery.config.DeliveryCodeProperties;
import com.delivery.delivery.mapper.DeliveryMapper;
import com.delivery.delivery.service.DeliveryAccessGuard;
import com.delivery.delivery.service.DeliveryCodeHasher;
import com.delivery.delivery.service.DeliveryService;
import com.delivery.inventory.mapper.InventoryMapper;
import com.delivery.inventory.service.InventoryService;
import com.delivery.notification.service.NotificationService;
import com.delivery.order.dto.CheckoutResult;
import com.delivery.order.mapper.OrderMapper;
import com.delivery.order.service.OrderService;
import com.delivery.order.service.OrderStockService;
import com.delivery.order.service.PriceChangedException;
import com.delivery.order.service.ZeroCheckoutDiscountResolver;
import com.delivery.payment.service.PaymentService;
import com.delivery.sales.config.SalesStatusOverrideProperties;
import com.delivery.sales.service.SalesAccessGuard;
import com.delivery.sales.service.SalesService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

/**
 * Real-Postgres checkout tests (INC-07: COR-06, COR-01, AS-22). Runs WITHOUT a test-managed
 * transaction so every service call commits for real and concurrent threads observe each
 * other through the database (unique constraint, row locks). Each test seeds a fresh tenant,
 * so leftover rows never interfere across tests.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=validate",
        "app.delivery.code-hmac-secret=test-only-delivery-code-hmac-secret"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({com.delivery.delivery.service.DeliveryRetirementService.class, OrderService.class, OrderStockService.class, InventoryService.class, InventoryMapper.class, CatalogAccessGuard.class,
        com.delivery.vendor.api.MerchantScopeGuard.class, com.delivery.vendor.config.MerchantScopeProperties.class,
        OrderMapper.class, ZeroCheckoutDiscountResolver.class, CartService.class, CartMapper.class, PricingService.class, com.delivery.vendor.api.StoreAvailabilityService.class, com.delivery.catalog.api.PharmacySalePolicy.class,
        SalesService.class, DeliveryService.class, DeliveryMapper.class,
        DeliveryAccessGuard.class, DeliveryCodeHasher.class, DeliveryCodeProperties.class,
        com.delivery.order.service.DeliveryAddressResolver.class})
class CheckoutConcurrencyPostgresTest {

    private static final boolean DOCKER_AVAILABLE = DockerClientFactory.instance().isDockerAvailable();
    private static final String KEYCLOAK_USER = "kc-customer";
    private static final String OTHER_KEYCLOAK_USER = "kc-other-customer";

    @BeforeAll
    static void requiresDockerOrExternalDatabase() {
        Assumptions.assumeTrue(DOCKER_AVAILABLE || System.getenv("SPRING_DATASOURCE_URL") != null,
                "Skipping checkout Postgres tests: no Docker environment and no SPRING_DATASOURCE_URL override");
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        if (!DOCKER_AVAILABLE) {
            return;
        }
        PostgresTestContainerConfig.POSTGRES.start();
        registry.add("spring.datasource.url", PostgresTestContainerConfig.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresTestContainerConfig.POSTGRES::getUsername);
        registry.add("spring.datasource.password", PostgresTestContainerConfig.POSTGRES::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "20");
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private OrderService orderService;
    @Autowired private SalesService salesService;
    @Autowired private DeliveryService deliveryService;

    /** LAC-12: storage HeadObject is out of scope for these database tests. */
    @MockitoBean private StoredUploadValidator storedUploadValidator;
    @MockitoBean private TenantContext tenantContext;
    @MockitoBean private NotificationService notificationService;
    @MockitoBean private PaymentService paymentService;
    @MockitoBean private AuditLogService auditLogService;
    @MockitoBean private SalesAccessGuard salesAccessGuard;
    @MockitoBean private SalesStatusOverrideProperties salesStatusOverrideProperties;

    private UUID tenantId;
    private UUID vendorId;
    private UUID skuId;
    private UUID customerId;

    @BeforeEach
    void seed() {
        tenantId = UUID.randomUUID();
        vendorId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        skuId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, 'T', ?, 'ACTIVE', 'MZN', ?, ?)",
                tenantId, "slug-" + tenantId, now(), now());
        jdbc.update("INSERT INTO vendors (id, tenant_id, name, status, verification_status, created_at, updated_at) VALUES (?, ?, 'V', 'ACTIVE', 'APPROVED', ?, ?)",
                vendorId, tenantId, now(), now());
        jdbc.update("INSERT INTO categories (id, tenant_id, name, slug, active, created_at, updated_at) VALUES (?, ?, 'C', ?, TRUE, ?, ?)",
                categoryId, tenantId, "slug-" + categoryId, now(), now());
        jdbc.update("""
                INSERT INTO products (id, tenant_id, vendor_id, category_id, name, status,
                                      requires_prescription_metadata, manual_validation_required, prohibited_fuel, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Arroz', 'ACTIVE', FALSE, FALSE, FALSE, ?, ?)""",
                productId, tenantId, vendorId, categoryId, now(), now());
        jdbc.update("INSERT INTO skus (id, tenant_id, product_id, sku_code, name, price, active, created_at, updated_at) VALUES (?, ?, ?, ?, 'Arroz 1kg', 100.00, TRUE, ?, ?)",
                skuId, tenantId, productId, "sku-" + skuId, now(), now());
        jdbc.update("INSERT INTO inventory_items (id, tenant_id, vendor_id, sku_id, quantity_available, quantity_reserved, status, created_at, updated_at, version) VALUES (?, ?, ?, ?, 100, 0, 'ACTIVE', ?, ?, 0)",
                UUID.randomUUID(), tenantId, vendorId, skuId, now(), now());
        insertProfile(customerId, KEYCLOAK_USER);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(tenantContext.currentKeycloakUserId()).thenReturn(Optional.of(KEYCLOAK_USER));
    }

    @Test
    void tenConcurrentCheckoutsWithSameKeyCreateOneOrderAndOneReservation() throws Exception {
        UUID cartId = insertCart(customerId, 2, "100.00");
        String key = "idem-" + UUID.randomUUID();
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<CheckoutResult>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<CheckoutResult> call = () -> {
                start.await();
                return orderService.checkout(cartId, key);
            };
            futures.add(pool.submit(call));
        }
        start.countDown();
        Set<UUID> orderIds = new HashSet<>();
        int created = 0;
        for (Future<CheckoutResult> future : futures) {
            CheckoutResult result = future.get(60, TimeUnit.SECONDS); // any exception fails the test
            orderIds.add(result.order().id());
            if (!result.replayed()) created++;
        }
        pool.shutdown();

        assertThat(orderIds).hasSize(1);
        assertThat(created).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM orders WHERE tenant_id = ?")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payments WHERE tenant_id = ?")).isEqualTo(1);
        assertThat(stock()).containsEntry("quantity_available", 98).containsEntry("quantity_reserved", 2);
    }

    @Test
    void concurrentSameKeyAcrossDifferentCartsYieldsOneOrderAndConflictsNot500() throws Exception {
        // Different carts do not share the cart row lock, so the losers reach the INSERT and hit the
        // unique constraint: this exercises the rollback + fresh-transaction re-read path.
        String key = "idem-" + UUID.randomUUID();
        int threads = 5;
        List<UUID> carts = new ArrayList<>();
        for (int i = 0; i < threads; i++) carts.add(insertCart(customerId, 1, "100.00"));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<CheckoutResult>> futures = new ArrayList<>();
        for (UUID cart : carts) {
            futures.add(pool.submit(() -> {
                start.await();
                return orderService.checkout(cart, key);
            }));
        }
        start.countDown();
        int created = 0;
        int conflicts = 0;
        for (Future<CheckoutResult> future : futures) {
            try {
                if (!future.get(60, TimeUnit.SECONDS).replayed()) created++;
            } catch (java.util.concurrent.ExecutionException failure) {
                assertThat(failure.getCause()).isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("idempotency_key_reused"));
                conflicts++;
            }
        }
        pool.shutdown();

        assertThat(created).isEqualTo(1);
        assertThat(conflicts).isEqualTo(threads - 1);
        assertThat(count("SELECT count(*) FROM orders WHERE tenant_id = ?")).isEqualTo(1);
        assertThat(stock()).containsEntry("quantity_available", 99).containsEntry("quantity_reserved", 1);
    }

    @Test
    void sameKeyWithAnotherCartIsRejectedWithConflict() {
        String key = "idem-" + UUID.randomUUID();
        UUID firstCart = insertCart(customerId, 1, "100.00");
        UUID firstOrder = orderService.checkout(firstCart, key).order().id();
        UUID secondCart = insertCart(customerId, 1, "100.00");

        BusinessException ex = catchThrowableOfType(() -> orderService.checkout(secondCart, key), BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo("idempotency_key_reused");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(count("SELECT count(*) FROM orders WHERE tenant_id = ?")).isEqualTo(1);
        assertThat(firstOrder).isNotNull();
    }

    @Test
    void sameKeyFromAnotherCustomerIsRejectedAndNeverLeaksTheOrder() {
        String key = "idem-" + UUID.randomUUID();
        orderService.checkout(insertCart(customerId, 1, "100.00"), key);
        UUID otherCustomer = UUID.randomUUID();
        insertProfile(otherCustomer, OTHER_KEYCLOAK_USER);
        UUID otherCart = insertCart(otherCustomer, 1, "100.00");
        when(tenantContext.currentKeycloakUserId()).thenReturn(Optional.of(OTHER_KEYCLOAK_USER));

        BusinessException ex = catchThrowableOfType(() -> orderService.checkout(otherCart, key), BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo("idempotency_key_reused");
    }

    @Test
    void sequentialRetryWithSameKeyAndCartReplaysTheOrder() {
        UUID cartId = insertCart(customerId, 1, "100.00");
        String key = "idem-" + UUID.randomUUID();
        CheckoutResult first = orderService.checkout(cartId, key);
        CheckoutResult retry = orderService.checkout(cartId, key);

        assertThat(first.replayed()).isFalse();
        assertThat(retry.replayed()).isTrue();
        assertThat(retry.order().id()).isEqualTo(first.order().id());
        assertThat(stock()).containsEntry("quantity_reserved", 1);
    }

    @Test
    void cancellationReleasesStockExactlyOnce() {
        UUID orderId = orderService.checkout(insertCart(customerId, 3, "100.00"), "idem-" + UUID.randomUUID()).order().id();
        assertThat(stock()).containsEntry("quantity_available", 97).containsEntry("quantity_reserved", 3);

        salesService.cancel(orderId, "customer asked");
        assertThat(stock()).containsEntry("quantity_available", 100).containsEntry("quantity_reserved", 0);

        // A second cancellation is refused by the state machine and must not release again.
        catchThrowableOfType(() -> salesService.cancel(orderId, "again"), BusinessException.class);
        assertThat(stock()).containsEntry("quantity_available", 100).containsEntry("quantity_reserved", 0);
        assertThat(jdbc.queryForObject("SELECT stock_status FROM orders WHERE id = ?", String.class, orderId)).isEqualTo("RELEASED");
    }

    @Test
    void pickupCommitsReservedStock() {
        UUID orderId = orderService.checkout(insertCart(customerId, 2, "100.00"), "idem-" + UUID.randomUUID()).order().id();
        UUID deliveryId = jdbc.queryForObject("SELECT id FROM deliveries WHERE order_id = ?", UUID.class, orderId);
        jdbc.update("UPDATE deliveries SET status = 'ARRIVED_AT_VENDOR' WHERE id = ?", deliveryId);

        // INC-02: only the assigned courier or OPS/ADMIN may move a delivery; act as OPS (no courier assigned here).
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("ops", null, "ROLE_OPS"));
        try {
            deliveryService.updateStatus(deliveryId, DeliveryStatus.PICKED_UP, null, null);
        } finally {
            SecurityContextHolder.clearContext();
        }

        // Reserve already moved the units out of available; pickup consumes the reservation.
        assertThat(stock()).containsEntry("quantity_available", 98).containsEntry("quantity_reserved", 0);
        assertThat(jdbc.queryForObject("SELECT stock_status FROM orders WHERE id = ?", String.class, orderId)).isEqualTo("COMMITTED");
    }

    @Test
    void changedPriceIsRejectedWithConflictAndCartIsRepricedForReconfirmation() {
        UUID cartId = insertCart(customerId, 2, "100.00");
        jdbc.update("UPDATE skus SET price = 120.00 WHERE id = ?", skuId);
        String key = "idem-" + UUID.randomUUID();

        PriceChangedException ex = catchThrowableOfType(() -> orderService.checkout(cartId, key), PriceChangedException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo("price_changed");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getChanges()).singleElement().satisfies(change -> {
            assertThat(change.skuId()).isEqualTo(skuId);
            assertThat(change.previousUnitPrice()).isEqualByComparingTo("100.00");
            assertThat(change.currentUnitPrice()).isEqualByComparingTo("120.00");
        });
        assertThat(count("SELECT count(*) FROM orders WHERE tenant_id = ?")).isZero();
        assertThat(stock()).containsEntry("quantity_reserved", 0);

        // The cart now carries the current price, so the customer can reconfirm and check out.
        UUID orderId = orderService.checkout(cartId, key).order().id();
        assertThat(jdbc.queryForObject("SELECT unit_price_snapshot FROM order_items WHERE order_id = ?", BigDecimal.class, orderId))
                .isEqualByComparingTo("120.00");
        assertThat(jdbc.queryForObject("SELECT subtotal FROM orders WHERE id = ?", BigDecimal.class, orderId))
                .isEqualByComparingTo("240.00");
    }

    @Test
    void inactiveProductIsRejectedAtCheckout() {
        UUID cartId = insertCart(customerId, 1, "100.00");
        jdbc.update("UPDATE products SET status = 'REJECTED' WHERE id = (SELECT product_id FROM skus WHERE id = ?)", skuId);

        BusinessException ex = catchThrowableOfType(() -> orderService.checkout(cartId, "idem-" + UUID.randomUUID()), BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo("product_not_available");
        assertThat(stock()).containsEntry("quantity_reserved", 0);
    }

    private UUID insertCart(UUID owner, int quantity, String unitPrice) {
        UUID cartId = UUID.randomUUID();
        BigDecimal subtotal = new BigDecimal(unitPrice).multiply(BigDecimal.valueOf(quantity));
        // Each test customer may hold several carts here; the service only reads them by id.
        jdbc.update("INSERT INTO carts (id, tenant_id, customer_id, vendor_id, status, subtotal, fees, taxes, discounts, total, created_at, updated_at, version) VALUES (?, ?, ?, ?, 'ACTIVE', ?, 0, 0, 0, ?, ?, ?, 0)",
                cartId, tenantId, owner, vendorId, subtotal, subtotal, now(), now());
        jdbc.update("INSERT INTO cart_items (id, cart_id, tenant_id, sku_id, product_name_snapshot, sku_name_snapshot, unit_price_snapshot, quantity, created_at) VALUES (?, ?, ?, ?, 'Arroz', 'Arroz 1kg', ?, ?, ?)",
                UUID.randomUUID(), cartId, tenantId, skuId, new BigDecimal(unitPrice), quantity, now());
        return cartId;
    }

    private void insertProfile(UUID id, String keycloakUserId) {
        jdbc.update("INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'Customer', 'ACTIVE', ?, ?)",
                id, tenantId, keycloakUserId, keycloakUserId + "@example.test", now(), now());
    }

    private Map<String, Object> stock() {
        return jdbc.queryForMap("SELECT quantity_available, quantity_reserved FROM inventory_items WHERE sku_id = ?", skuId);
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class, tenantId);
        return value == null ? 0 : value;
    }

    private static Timestamp now() {
        return Timestamp.from(Instant.now());
    }
}
