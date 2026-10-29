package com.delivery.order;

import com.delivery.upload.service.StoredUploadValidator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.delivery.cart.mapper.CartMapper;
import com.delivery.cart.service.CartService;
import com.delivery.cart.service.PricingService;
import com.delivery.catalog.service.CatalogAccessGuard;
import com.delivery.common.PostgresTestContainerConfig;
import com.delivery.common.TestParents;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.delivery.config.DeliveryCodeProperties;
import com.delivery.common.exception.BusinessException;
import com.delivery.delivery.entity.DeliveryStatus;
import com.delivery.delivery.mapper.DeliveryMapper;
import com.delivery.delivery.service.DeliveryAccessGuard;
import com.delivery.delivery.service.DeliveryCodeHasher;
import com.delivery.delivery.service.DeliveryService;
import com.delivery.inventory.mapper.InventoryMapper;
import com.delivery.inventory.service.InventoryService;
import com.delivery.notification.service.NotificationService;
import com.delivery.order.mapper.OrderMapper;
import com.delivery.order.service.OrderService;
import com.delivery.order.service.OrderStockService;
import com.delivery.order.service.ZeroCheckoutDiscountResolver;
import com.delivery.payment.config.PaymentMockConfirmationProperties;
import com.delivery.payment.dto.RefundRequest;
import com.delivery.payment.dto.RefundResponse;
import com.delivery.payment.mapper.PaymentMapper;
import com.delivery.payment.service.PaymentService;
import com.delivery.sales.config.SalesStatusOverrideProperties;
import com.delivery.sales.service.SalesAccessGuard;
import com.delivery.sales.service.SalesService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

/**
 * Real-Postgres tests for the gaps surfaced by the V042/V043 data fixes, each committed in its own transaction:
 * a full refund clears the plain PIN, a status override to DELIVERED commits the stock like a real delivery does, a
 * full refund before pickup gives the reservation back (V043 rule), and a full refund / cancellation / seller
 * rejection before pickup retires the delivery so the courier can no longer pick up or complete it.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=validate",
        "app.delivery.code-hmac-secret=test-only-delivery-code-hmac-secret",
        "app.sales.status-override.enabled=true"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({com.delivery.delivery.service.DeliveryRetirementService.class, OrderService.class, OrderStockService.class, InventoryService.class, InventoryMapper.class, CatalogAccessGuard.class,
        com.delivery.vendor.api.MerchantScopeGuard.class, com.delivery.vendor.config.MerchantScopeProperties.class,
        OrderMapper.class, ZeroCheckoutDiscountResolver.class, CartService.class, CartMapper.class, PricingService.class, com.delivery.vendor.api.StoreAvailabilityService.class,
        PaymentService.class, PaymentMapper.class, PaymentMockConfirmationProperties.class,
        DeliveryCodeHasher.class, DeliveryCodeProperties.class, com.delivery.order.service.DeliveryAddressResolver.class,
        SalesService.class, SalesAccessGuard.class, SalesStatusOverrideProperties.class,
        DeliveryService.class, DeliveryMapper.class, DeliveryAccessGuard.class})
class RefundAndOverrideStockPinPostgresTest {

    private static final String COURIER_USER = "kc-courier-";
    private static final boolean DOCKER_AVAILABLE = DockerClientFactory.instance().isDockerAvailable();

    @BeforeAll
    static void requiresDockerOrExternalDatabase() {
        Assumptions.assumeTrue(DOCKER_AVAILABLE || System.getenv("SPRING_DATASOURCE_URL") != null,
                "Skipping refund/override stock+PIN Postgres test: no Docker environment and no SPRING_DATASOURCE_URL override");
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
        // Sequential tests: a 2-connection pool is enough, and this extra context must not push the shared container
        // over max_connections in a full `mvn verify` (see PostgresApiE2ETestBase).
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "2");
    }

    @Autowired private SalesService salesService;
    @Autowired private PaymentService paymentService;
    @Autowired private OrderService orderService;
    @Autowired private DeliveryService deliveryService;
    @Autowired private JdbcTemplate jdbc;
    /** LAC-12: storage HeadObject is out of scope for these database tests. */
    @MockitoBean private StoredUploadValidator storedUploadValidator;
    @MockitoBean private TenantContext tenantContext;
    @MockitoBean private NotificationService notificationService;
    @MockitoBean private AuditLogService auditLogService;

    private UUID tenantId;
    private UUID vendorId;
    private UUID skuId;
    private UUID orderId;
    private UUID paymentId;

    @BeforeEach
    void seed() {
        tenantId = UUID.randomUUID();
        vendorId = UUID.randomUUID();
        skuId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', 'MZN', ?, ?)",
                tenantId, "Stock/PIN Tenant", "stockpin-" + tenantId, now, now);
        jdbc.update("INSERT INTO vendors (id, tenant_id, name, status, verification_status, created_at, updated_at) VALUES (?, ?, 'V', 'ACTIVE', 'APPROVED', ?, ?)",
                vendorId, tenantId, now, now);
        jdbc.update("INSERT INTO categories (id, tenant_id, name, slug, active, created_at, updated_at) VALUES (?, ?, 'C', ?, TRUE, ?, ?)",
                categoryId, tenantId, "slug-" + categoryId, now, now);
        jdbc.update("""
                INSERT INTO products (id, tenant_id, vendor_id, category_id, name, status,
                                      requires_prescription_metadata, manual_validation_required, prohibited_fuel, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Arroz', 'ACTIVE', FALSE, FALSE, FALSE, ?, ?)""",
                productId, tenantId, vendorId, categoryId, now, now);
        jdbc.update("INSERT INTO skus (id, tenant_id, product_id, sku_code, name, price, active, created_at, updated_at) VALUES (?, ?, ?, ?, 'Arroz 1kg', 18.00, TRUE, ?, ?)",
                skuId, tenantId, productId, "sku-" + skuId, now, now);
        // 5 units reserved by this order at checkout (INC-07): on-hand 100 = 95 available + 5 reserved.
        jdbc.update("INSERT INTO inventory_items (id, tenant_id, vendor_id, sku_id, quantity_available, quantity_reserved, status, created_at, updated_at, version) VALUES (?, ?, ?, ?, 95, 5, 'ACTIVE', ?, ?, 0)",
                UUID.randomUUID(), tenantId, vendorId, skuId, now, now);
        jdbc.update("""
                INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, status, subtotal, fees, taxes, discounts,
                                    discount_total, total, checkout_idempotency_key, delivery_confirmation_code_hash,
                                    delivery_confirmation_code_display, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'PAYMENT_CONFIRMED', 90, 10, 0, 0, 0, 100, ?, 'hash', '000000', ?, ?)""",
                orderId, tenantId, "ref-" + orderId, TestParents.customer(jdbc, tenantId), vendorId, "key-" + orderId, now, now);
        jdbc.update("INSERT INTO order_items (id, order_id, tenant_id, sku_id, product_name_snapshot, sku_name_snapshot, unit_price_snapshot, quantity, line_total, created_at) VALUES (?, ?, ?, ?, 'Arroz', 'Arroz 1kg', 18.00, 5, 90.00, ?)",
                UUID.randomUUID(), orderId, tenantId, skuId, now);
        jdbc.update("INSERT INTO payments (id, tenant_id, order_id, amount, provider, idempotency_key, status, created_at, updated_at) VALUES (?, ?, ?, 100.00, 'LOCAL_MOCK', ?, 'CONFIRMED', ?, ?)",
                paymentId, tenantId, orderId, "pay-" + paymentId, now, now);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        authenticate("ROLE_OPS", "ROLE_FINANCE");
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        // Removes only the rows this test seeded (throwaway Testcontainers database).
        jdbc.update("DELETE FROM delivery_events WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM dispatch_jobs WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM deliveries WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM couriers WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM refunds WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM payments WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM order_items WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM orders WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM inventory_items WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM skus WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM products WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM categories WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM vendors WHERE tenant_id = ?", tenantId);
        // After orders: orders.customer_id references app_user_profiles (LAC-58).
        jdbc.update("DELETE FROM app_user_profiles WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM tenants WHERE id = ?", tenantId);
    }

    // --- Gap 1: PIN on a fully refunded order (V042 rule) ---

    @Test
    void fullRefundBeforeDispatchClearsThePlainPin() {
        approveFullRefund();

        Map<String, Object> order = order();
        assertThat(order).containsEntry("status", "REFUNDED");
        assertThat(order.get("delivery_confirmation_code_display")).isNull();
        assertThat(paymentStatus()).isEqualTo("FULLY_REFUNDED");
    }

    @Test
    void fullRefundWithACourierEngagedBeforePickupRetiresTheDeliveryClearsThePinAndReleasesOnce() {
        jdbc.update("UPDATE orders SET status = 'ASSIGNED_TO_COURIER' WHERE id = ?", orderId);
        UUID deliveryId = deliveryWithCourier("ACCEPTED", "ACCEPTED");

        approveFullRefund();

        Map<String, Object> order = order();
        assertThat(order).containsEntry("status", "REFUNDED").containsEntry("stock_status", "RELEASED");
        assertThat(order.get("delivery_confirmation_code_display")).isNull();
        assertThat(deliveryStatus(deliveryId)).isEqualTo("CANCELLED");
        assertThat(jobStatus(deliveryId)).isEqualTo("CANCELLED");
        assertThat(retiredEvents(deliveryId)).isEqualTo(1);
        assertThat(inventory()).containsEntry("quantity_available", 100).containsEntry("quantity_reserved", 0);

        // The courier can no longer pick up or complete; nothing moves and the stock is not touched again.
        actAsCourier();
        assertThatThrownBy(() -> deliveryService.updateStatus(deliveryId, DeliveryStatus.ARRIVED_AT_VENDOR, null, null))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getStatus().value()).isEqualTo(409));
        assertThatThrownBy(() -> deliveryService.complete(deliveryId, "000000"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getStatus().value()).isEqualTo(409));
        assertThat(deliveryStatus(deliveryId)).isEqualTo("CANCELLED");
        assertThat(order()).containsEntry("status", "REFUNDED").containsEntry("stock_status", "RELEASED");
        assertThat(inventory()).containsEntry("quantity_available", 100).containsEntry("quantity_reserved", 0);
        assertThat(jdbc.queryForObject("SELECT confirmation_attempts FROM deliveries WHERE id = ?", Integer.class, deliveryId)).isZero();
    }

    // --- Gap 2: manual DELIVERED closes the stock lifecycle like a real delivery ---

    @Test
    void statusOverrideToDeliveredCommitsTheReservationAndClearsThePlainPin() {
        jdbc.update("UPDATE orders SET status = 'DELIVERING' WHERE id = ?", orderId);
        delivery("ON_ROUTE_TO_CUSTOMER");

        assertThat(salesService.statusOverride(orderId, "DELIVERED", "courier device died").orderStatus()).isEqualTo("DELIVERED");

        Map<String, Object> order = order();
        assertThat(order).containsEntry("status", "DELIVERED").containsEntry("stock_status", "COMMITTED");
        assertThat(order.get("delivery_confirmation_code_display")).isNull();
        // commit drains reserved only: on-hand 100 -> 95 (available untouched, reserved consumed).
        assertThat(inventory()).containsEntry("quantity_available", 95).containsEntry("quantity_reserved", 0);

        // Idempotent with a later courier pickup / completion: nothing to commit twice.
        salesService.statusOverride(orderId, "DELIVERED", "retry");
        assertThat(inventory()).containsEntry("quantity_available", 95).containsEntry("quantity_reserved", 0);
    }

    // --- Gap 3: full refund before pickup gives the reservation back (V043 rule) ---

    @Test
    void fullRefundBeforePickupReleasesTheReservationAndMarksTheOrderRefunded() {
        delivery("ACCEPTED"); // courier engaged but goods still in the store

        approveFullRefund();

        Map<String, Object> order = order();
        // INC-06 behaviour kept: a non-CANCELLED order becomes REFUNDED; the release is the only addition.
        assertThat(order).containsEntry("status", "REFUNDED").containsEntry("stock_status", "RELEASED");
        assertThat(inventory()).containsEntry("quantity_available", 100).containsEntry("quantity_reserved", 0);
        assertThat(paymentStatus()).isEqualTo("FULLY_REFUNDED");
    }

    @Test
    void fullRefundAfterPickupLeavesTheStockAlone() {
        // Goods left the store; the row is seeded RESERVED on purpose to prove nothing is released (in production
        // PICKED_UP would already have committed it and release would be a no-op anyway).
        jdbc.update("UPDATE orders SET status = 'DELIVERING' WHERE id = ?", orderId);
        UUID deliveryId = deliveryWithCourier("ON_ROUTE_TO_CUSTOMER", "ACCEPTED");

        approveFullRefund();

        Map<String, Object> order = order();
        assertThat(order).containsEntry("status", "REFUNDED").containsEntry("stock_status", "RESERVED");
        assertThat(inventory()).containsEntry("quantity_available", 95).containsEntry("quantity_reserved", 5);
        // Goods left the store: the delivery and its job are not retired...
        assertThat(deliveryStatus(deliveryId)).isEqualTo("ON_ROUTE_TO_CUSTOMER");
        assertThat(jobStatus(deliveryId)).isEqualTo("ACCEPTED");
        assertThat(retiredEvents(deliveryId)).isZero();
        // ...but the REFUNDED order can no longer be progressed or completed (409 order_not_deliverable).
        actAsCourier();
        assertThatThrownBy(() -> deliveryService.updateStatus(deliveryId, DeliveryStatus.ARRIVED_AT_CUSTOMER, null, null))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("order_not_deliverable"));
        assertThat(deliveryStatus(deliveryId)).isEqualTo("ON_ROUTE_TO_CUSTOMER");
    }

    // --- Cancellations before pickup retire the delivery ---

    @Test
    void sellerRejectionRetiresTheDeliveryAndTheCourierCanNoLongerCompleteIt() {
        UUID deliveryId = deliveryWithCourier("ASSIGNED", "ASSIGNED");

        orderService.rejectByVendor(orderId, "Sem stock");

        assertThat(order()).containsEntry("status", "CANCELLED").containsEntry("stock_status", "RELEASED");
        assertThat(deliveryStatus(deliveryId)).isEqualTo("CANCELLED");
        assertThat(jobStatus(deliveryId)).isEqualTo("CANCELLED");
        assertThat(inventory()).containsEntry("quantity_available", 100).containsEntry("quantity_reserved", 0);

        actAsCourier();
        assertThatThrownBy(() -> deliveryService.complete(deliveryId, "000000"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getStatus().value()).isEqualTo(409));
        assertThat(deliveryStatus(deliveryId)).isEqualTo("CANCELLED");
        assertThat(inventory()).containsEntry("quantity_available", 100).containsEntry("quantity_reserved", 0);
    }

    @Test
    void fullRefundOfASaleCancelledEarlierDoesNotReleaseTwice() {
        UUID deliveryId = delivery("DISPATCH_PENDING"); // created at checkout
        salesService.cancel(orderId, "cliente desistiu"); // releases once, retires the delivery
        assertThat(inventory()).containsEntry("quantity_available", 100).containsEntry("quantity_reserved", 0);
        assertThat(deliveryStatus(deliveryId)).isEqualTo("CANCELLED");

        approveFullRefund();
        assertThat(retiredEvents(deliveryId)).isEqualTo(1); // not retired twice

        Map<String, Object> order = order();
        assertThat(order).containsEntry("status", "CANCELLED").containsEntry("stock_status", "RELEASED");
        assertThat(inventory()).containsEntry("quantity_available", 100).containsEntry("quantity_reserved", 0);
    }

    // --- helpers ---

    private void approveFullRefund() {
        RefundResponse refund = paymentService.requestRefund(paymentId, new RefundRequest(new BigDecimal("100.00"), "full", "full-" + orderId));
        paymentService.approveRefund(refund.id());
    }

    private UUID delivery(String status) {
        Timestamp now = Timestamp.from(Instant.now());
        UUID deliveryId = UUID.randomUUID();
        jdbc.update("INSERT INTO deliveries (id, tenant_id, order_id, courier_id, status, confirmation_code_hash, confirmation_attempts, created_at, updated_at) VALUES (?, ?, ?, NULL, ?, 'hash', 0, ?, ?)",
                deliveryId, tenantId, orderId, status, now, now);
        return deliveryId;
    }

    /** A delivery held by a real courier (profile COURIER_USER) with its dispatch job in {@code jobStatus}. */
    private UUID deliveryWithCourier(String status, String jobStatus) {
        Timestamp now = Timestamp.from(Instant.now());
        UUID profileId = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'Courier', 'ACTIVE', ?, ?)",
                profileId, tenantId, COURIER_USER + tenantId, "c-" + tenantId + "@test.local", now, now);
        UUID courierId = UUID.randomUUID();
        jdbc.update("INSERT INTO couriers (id, tenant_id, user_profile_id, verification_status, available, created_at, updated_at) VALUES (?, ?, ?, 'APPROVED', TRUE, ?, ?)",
                courierId, tenantId, profileId, now, now);
        UUID deliveryId = UUID.randomUUID();
        jdbc.update("INSERT INTO deliveries (id, tenant_id, order_id, courier_id, status, confirmation_code_hash, confirmation_attempts, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'hash', 0, ?, ?)",
                deliveryId, tenantId, orderId, courierId, status, now, now);
        jdbc.update("INSERT INTO dispatch_jobs (id, tenant_id, order_id, delivery_id, courier_id, status, assigned_at, created_at, updated_at, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                UUID.randomUUID(), tenantId, orderId, deliveryId, courierId, jobStatus, now, now, now);
        return deliveryId;
    }

    private void actAsCourier() {
        when(tenantContext.currentKeycloakUserId()).thenReturn(Optional.of(COURIER_USER + tenantId));
        authenticate("ROLE_COURIER");
    }

    private String deliveryStatus(UUID deliveryId) {
        return jdbc.queryForObject("SELECT status FROM deliveries WHERE id = ?", String.class, deliveryId);
    }

    private String jobStatus(UUID deliveryId) {
        return jdbc.queryForObject("SELECT status FROM dispatch_jobs WHERE delivery_id = ?", String.class, deliveryId);
    }

    private int retiredEvents(UUID deliveryId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM delivery_events WHERE delivery_id = ? AND event_type = 'RETIRED'", Integer.class, deliveryId);
    }

    private Map<String, Object> order() {
        return jdbc.queryForMap("SELECT status, stock_status, delivery_confirmation_code_display FROM orders WHERE id = ?", orderId);
    }

    private Map<String, Object> inventory() {
        return jdbc.queryForMap("SELECT quantity_available, quantity_reserved FROM inventory_items WHERE sku_id = ?", skuId);
    }

    private String paymentStatus() {
        return jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, paymentId);
    }

    private static void authenticate(String... roles) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("user")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList(roles)));
    }
}
