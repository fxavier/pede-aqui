package com.delivery.order;

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
import com.delivery.delivery.config.DeliveryCodeProperties;
import com.delivery.delivery.service.DeliveryCodeHasher;
import com.delivery.inventory.mapper.InventoryMapper;
import com.delivery.inventory.service.InventoryService;
import com.delivery.notification.service.NotificationService;
import com.delivery.order.mapper.OrderMapper;
import com.delivery.order.service.OrderService;
import com.delivery.order.service.OrderStockService;
import com.delivery.order.service.ZeroCheckoutDiscountResolver;
import com.delivery.payment.config.PaymentMockConfirmationProperties;
import com.delivery.payment.mapper.PaymentMapper;
import com.delivery.payment.service.PaymentService;
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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

/**
 * Characterisation test (INC-04 x INC-06 x INC-07) documenting CURRENT behaviour, not a product decision:
 * a vendor rejection asks for a refund of the FULL payment amount, so once a partial refund already exists
 * INC-06's cap (amount <= payment - non-rejected refunds) refuses it with 422 and the whole rejection rolls
 * back — the order stays PAYMENT_CONFIRMED, keeps its stock reservation and no refund row is added.
 * Whether rejection should instead refund only the remainder is an open product decision.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=validate",
        "app.delivery.code-hmac-secret=test-only-delivery-code-hmac-secret"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({OrderService.class, OrderStockService.class, InventoryService.class, InventoryMapper.class, CatalogAccessGuard.class,
        OrderMapper.class, ZeroCheckoutDiscountResolver.class, CartService.class, CartMapper.class, PricingService.class,
        PaymentService.class, PaymentMapper.class, PaymentMockConfirmationProperties.class,
        DeliveryCodeHasher.class, DeliveryCodeProperties.class})
class VendorRejectionAfterPartialRefundPostgresTest {

    private static final boolean DOCKER_AVAILABLE = DockerClientFactory.instance().isDockerAvailable();

    @BeforeAll
    static void requiresDockerOrExternalDatabase() {
        Assumptions.assumeTrue(DOCKER_AVAILABLE || System.getenv("SPRING_DATASOURCE_URL") != null,
                "Skipping vendor rejection Postgres test: no Docker environment and no SPRING_DATASOURCE_URL override");
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
    }

    @Autowired private OrderService orderService;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private TenantContext tenantContext;
    @MockitoBean private NotificationService notificationService;
    @MockitoBean private AuditLogService auditLogService;

    private UUID tenantId;
    private UUID orderId;
    private UUID paymentId;

    @BeforeEach
    void seed() {
        tenantId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', 'MZN', ?, ?)",
                tenantId, "Rejection Tenant", "reject-" + tenantId, now, now);
        jdbc.update("""
                INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, status, subtotal, fees, taxes, discounts,
                                    discount_total, total, checkout_idempotency_key, delivery_confirmation_code_hash,
                                    delivery_confirmation_code_display, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'PAYMENT_CONFIRMED', 90, 10, 0, 0, 0, 100, ?, 'hash', '000000', ?, ?)""",
                orderId, tenantId, "ref-" + orderId, UUID.randomUUID(), UUID.randomUUID(), "key-" + orderId, now, now);
        jdbc.update("INSERT INTO payments (id, tenant_id, order_id, amount, provider, idempotency_key, status, created_at, updated_at) VALUES (?, ?, ?, 100.00, 'LOCAL_MOCK', ?, 'CONFIRMED', ?, ?)",
                paymentId, tenantId, orderId, "pay-" + paymentId, now, now);
        // Existing partial refund (30 of 100), e.g. requested earlier by support/finance.
        jdbc.update("INSERT INTO refunds (id, tenant_id, payment_id, order_id, amount, reason, status, idempotency_key, created_at, updated_at) VALUES (?, ?, ?, ?, 30.00, 'partial', 'REQUESTED', ?, ?, ?)",
                UUID.randomUUID(), tenantId, paymentId, orderId, "partial-" + orderId, now, now);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("ops", null, "ROLE_OPS"));
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        // Removes only the rows this test seeded (throwaway Testcontainers database).
        jdbc.update("DELETE FROM refunds WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM payments WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM orders WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM tenants WHERE id = ?", tenantId);
    }

    @Test
    void rejectionAfterPartialRefundIsRefusedWith422AndRollsBackEverything() {
        BusinessException ex = catchThrowableOfType(() -> orderService.rejectByVendor(orderId, "Sem stock"), BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ex.getCode()).isEqualTo("invalid_refund_amount");
        Map<String, Object> order = jdbc.queryForMap("SELECT status, rejection_reason, stock_status FROM orders WHERE id = ?", orderId);
        assertThat(order).containsEntry("status", "PAYMENT_CONFIRMED")
                .containsEntry("stock_status", "RESERVED");
        assertThat(order.get("rejection_reason")).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refunds WHERE payment_id = ?", Integer.class, paymentId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM refunds WHERE payment_id = ?", BigDecimal.class, paymentId))
                .isEqualByComparingTo("30.00");
    }
}
