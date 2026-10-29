package com.delivery.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.cart.mapper.CartMapper;
import com.delivery.cart.service.CartService;
import com.delivery.cart.service.PricingService;
import com.delivery.catalog.service.CatalogAccessGuard;
import com.delivery.common.PostgresTestContainerConfig;
import com.delivery.common.TestParents;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.delivery.config.DeliveryCodeProperties;
import com.delivery.delivery.service.DeliveryCodeHasher;
import com.delivery.inventory.mapper.InventoryMapper;
import com.delivery.inventory.service.InventoryService;
import com.delivery.notification.service.NotificationService;
import com.delivery.order.dto.OrderResponse;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.mapper.OrderMapper;
import com.delivery.order.service.OrderService;
import com.delivery.order.service.OrderStockService;
import com.delivery.order.service.ZeroCheckoutDiscountResolver;
import com.delivery.payment.config.PaymentMockConfirmationProperties;
import com.delivery.payment.mapper.PaymentMapper;
import com.delivery.payment.service.PaymentService;
import com.delivery.common.exception.BusinessException;
import com.delivery.sales.config.SalesStatusOverrideProperties;
import com.delivery.sales.dto.SalesActionResponse;
import com.delivery.sales.service.SalesAccessGuard;
import com.delivery.sales.service.SalesService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * Real-Postgres tests for the unified seller rejection (INC-04 x INC-06 x INC-07, product decision "every seller
 * rejection is a cancellation"): the order is CANCELLED with reason SELLER_REJECTED, only the remaining refundable
 * amount (payment − REFUNDED − pending refunds; the full amount when nothing was refunded) is requested, nothing is
 * created when nothing remains, stock is released once, the plain PIN is cleared, and a repeated or concurrent
 * rejection — or one racing an ops cancellation — is idempotent and never deadlocks. Legacy REJECTED_BY_VENDOR rows
 * and orders cancelled by other paths are returned as-is.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=validate",
        "app.delivery.code-hmac-secret=test-only-delivery-code-hmac-secret"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({com.delivery.delivery.service.DeliveryRetirementService.class, OrderService.class, OrderStockService.class, InventoryService.class, InventoryMapper.class, CatalogAccessGuard.class,
        com.delivery.vendor.api.MerchantScopeGuard.class, com.delivery.vendor.config.MerchantScopeProperties.class,
        OrderMapper.class, ZeroCheckoutDiscountResolver.class, CartService.class, CartMapper.class, PricingService.class,
        PaymentService.class, PaymentMapper.class, PaymentMockConfirmationProperties.class,
        DeliveryCodeHasher.class, DeliveryCodeProperties.class, com.delivery.order.service.DeliveryAddressResolver.class,
        SalesService.class, SalesAccessGuard.class, SalesStatusOverrideProperties.class})
class VendorRejectionAfterPartialRefundPostgresTest {

    private static final boolean DOCKER_AVAILABLE = DockerClientFactory.instance().isDockerAvailable();
    private static final String AUDIT_ACTION = "ORDER_CANCELLED_SELLER_REJECTED";

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
    @Autowired private SalesService salesService;
    @Autowired private PaymentService paymentService;
    @Autowired private JdbcTemplate jdbc;
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
                tenantId, "Rejection Tenant", "reject-" + tenantId, now, now);
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
        // 5 units reserved by this order at checkout (INC-07).
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
        authenticate(null, "ROLE_OPS");
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        // Removes only the rows this test seeded (throwaway Testcontainers database).
        jdbc.update("DELETE FROM refunds WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM payments WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM order_items WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM orders WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM inventory_items WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM skus WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM products WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM categories WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM vendors WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM app_user_profiles WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM tenants WHERE id = ?", tenantId);
    }

    @Test
    void rejectionAfterPendingPartialRefundCancelsOrderAndRefundsOnlyTheRemainder() {
        UUID partial = refund("30.00", "REQUESTED");

        OrderResponse response = orderService.rejectByVendor(orderId, "Sem stock");

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertCancelledBySeller();
        assertStockReleasedOnce();
        assertThat(newRefunds(partial)).singleElement().satisfies(r -> {
            assertThat((BigDecimal) r.get("amount")).isEqualByComparingTo("70.00");
            assertThat(r).containsEntry("status", "REQUESTED").containsEntry("idempotency_key", "seller-reject-" + orderId);
        });
        assertThat(refundTotal()).isEqualByComparingTo("100.00");
        verify(auditLogService).log(AUDIT_ACTION, "order", orderId.toString(), "refund_remaining=70.00", "SUCCESS");
        assertThat(paymentStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void completedAndPendingRefundsAreBothDeductedAndRejectedOnesAreNot() {
        UUID done = refund("20.00", "REFUNDED");
        UUID pending = refund("15.00", "REQUESTED");
        UUID rejected = refund("50.00", "REJECTED");

        orderService.rejectByVendor(orderId, "Sem stock");

        assertCancelledBySeller();
        assertThat(newRefunds(done, pending, rejected)).singleElement()
                .satisfies(r -> assertThat((BigDecimal) r.get("amount")).isEqualByComparingTo("65.00"));
    }

    @Test
    void alreadyFullyCoveredPaymentGetsNoNewRefund() {
        refund("40.00", "REFUNDED");
        refund("60.00", "REQUESTED");

        orderService.rejectByVendor(orderId, "Sem stock");

        assertCancelledBySeller();
        assertStockReleasedOnce();
        assertThat(refundCount()).isEqualTo(2);
        verify(auditLogService).log(AUDIT_ACTION, "order", orderId.toString(), "refund_remaining=0.00", "SUCCESS");
    }

    @Test
    void repeatedRejectionIsIdempotent() {
        refund("30.00", "REQUESTED");

        orderService.rejectByVendor(orderId, "Sem stock");
        OrderResponse again = orderService.rejectByVendor(orderId, "Outro motivo");

        assertThat(again.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(refundCount()).isEqualTo(2);
        assertThat(refundTotal()).isEqualByComparingTo("100.00");
        assertStockReleasedOnce();
        assertThat(jdbc.queryForObject("SELECT rejection_reason FROM orders WHERE id = ?", String.class, orderId)).isEqualTo("Sem stock");
        verify(auditLogService, times(1)).log(eq(AUDIT_ACTION), anyString(), anyString(), anyString(), anyString());
        verify(notificationService, times(1)).create(org.mockito.ArgumentMatchers.any(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void concurrentDoubleRejectionCreatesExactlyOneRefund() throws Exception {
        refund("30.00", "REQUESTED");
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    authenticate(null, "ROLE_OPS");
                    start.await();
                    try {
                        return orderService.rejectByVendor(orderId, "Sem stock");
                    } catch (RuntimeException e) {
                        return e;
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            start.countDown();
            for (Future<Object> future : futures) {
                assertThat(future.get(30, TimeUnit.SECONDS)).isInstanceOfSatisfying(OrderResponse.class,
                        r -> assertThat(r.status()).isEqualTo(OrderStatus.CANCELLED));
            }
        } finally {
            pool.shutdownNow();
        }

        assertCancelledBySeller();
        assertThat(refundCount()).isEqualTo(2);
        assertThat(refundTotal()).isEqualByComparingTo("100.00");
        assertStockReleasedOnce();
        verify(auditLogService, times(1)).log(eq(AUDIT_ACTION), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void approvalsMovePaymentToPartiallyThenFullyRefundedWhileCancelledOrderStaysCancelled() {
        UUID partial = refund("30.00", "REQUESTED");
        orderService.rejectByVendor(orderId, "Sem stock");
        UUID remainder = newRefunds(partial).get(0).get("id") instanceof UUID id ? id : null;

        paymentService.approveRefund(partial);
        assertThat(paymentStatus()).isEqualTo("PARTIALLY_REFUNDED");
        assertThat(orderStatus()).isEqualTo("CANCELLED");

        paymentService.approveRefund(remainder);
        assertThat(paymentStatus()).isEqualTo("FULLY_REFUNDED");
        assertThat(orderStatus()).isEqualTo("CANCELLED");
        verify(auditLogService, never()).log(eq("ORDER_REFUNDED"), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void failureInsideTheRejectionRollsEverythingBack() {
        UUID partial = refund("30.00", "REQUESTED");
        doThrow(new IllegalStateException("notification store down")).when(notificationService)
                .create(org.mockito.ArgumentMatchers.any(), anyString(), anyString(), anyString(), anyString(), anyString());

        assertThat(catchThrowable(() -> orderService.rejectByVendor(orderId, "Sem stock"))).isInstanceOf(IllegalStateException.class);

        Map<String, Object> order = jdbc.queryForMap("SELECT status, cancellation_reason, stock_status FROM orders WHERE id = ?", orderId);
        assertThat(order).containsEntry("status", "PAYMENT_CONFIRMED").containsEntry("stock_status", "RESERVED");
        assertThat(order.get("cancellation_reason")).isNull();
        assertThat(newRefunds(partial)).isEmpty();
        assertThat(inventory()).containsEntry("quantity_available", 95).containsEntry("quantity_reserved", 5);
    }

    @Test
    void anotherVendorCannotRejectTheOrder() {
        refund("30.00", "REQUESTED");
        authenticate(UUID.randomUUID(), "ROLE_VENDOR_ADMIN");

        assertThat(catchThrowable(() -> orderService.rejectByVendor(orderId, "Sem stock"))).isInstanceOf(NotFoundException.class);

        assertThat(orderStatus()).isEqualTo("PAYMENT_CONFIRMED");
        assertThat(refundCount()).isEqualTo(1);
        assertThat(inventory()).containsEntry("quantity_available", 95).containsEntry("quantity_reserved", 5);
    }

    @Test
    void ownVendorCanRejectTheOrder() {
        refund("30.00", "REQUESTED");
        authenticate(vendorId, "ROLE_VENDOR_ADMIN");

        assertThat(orderService.rejectByVendor(orderId, "Sem stock").status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void rejectionWithoutPriorRefundCancelsOrderAndRefundsTheFullAmount() {
        refund("30.00", "REJECTED"); // a rejected refund does not count as a prior refund

        OrderResponse response = orderService.rejectByVendor(orderId, "Sem stock");

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.cancellationReason()).isEqualTo(com.delivery.order.entity.CancellationReason.SELLER_REJECTED);
        assertCancelledBySeller(); // CANCELLED + SELLER_REJECTED + vendor text + PIN cleared
        assertStockReleasedOnce();
        assertThat(jdbc.queryForList("SELECT amount, status FROM refunds WHERE idempotency_key = ?", "seller-reject-" + orderId)).singleElement().satisfies(r -> {
            assertThat((BigDecimal) r.get("amount")).isEqualByComparingTo("100.00");
            assertThat(r).containsEntry("status", "REQUESTED");
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refunds WHERE idempotency_key = ?", Integer.class, "vendor-rejection-" + orderId)).isZero();
        assertThat(refundTotal()).isEqualByComparingTo("100.00");
        assertThat(paymentStatus()).isEqualTo("CONFIRMED"); // financial state moves only on refund approval
        verify(auditLogService).log(AUDIT_ACTION, "order", orderId.toString(), "refund_remaining=100.00", "SUCCESS");
        verify(auditLogService, never()).log(eq("ORDER_REJECTED"), anyString(), anyString(), anyString(), anyString());
        verify(notificationService, times(1)).create(org.mockito.ArgumentMatchers.any(), eq("CUSTOMER"), eq("order_rejected"), anyString(), anyString(), anyString());
    }

    @Test
    void legacyRejectedOrderIsReturnedAsIsOnRepeatRejection() {
        // Row written by the pre-unification path: REJECTED_BY_VENDOR + full refund keyed vendor-rejection-<orderId>.
        jdbc.update("UPDATE orders SET status = 'REJECTED_BY_VENDOR', rejection_reason = 'Antigo', stock_status = 'RELEASED' WHERE id = ?", orderId);
        jdbc.update("UPDATE inventory_items SET quantity_available = 100, quantity_reserved = 0 WHERE sku_id = ?", skuId);
        jdbc.update("INSERT INTO refunds (id, tenant_id, payment_id, order_id, amount, reason, status, idempotency_key, created_at, updated_at) VALUES (?, ?, ?, ?, 100.00, 'legacy', 'REQUESTED', ?, now(), now())",
                UUID.randomUUID(), tenantId, paymentId, orderId, "vendor-rejection-" + orderId);

        OrderResponse response = orderService.rejectByVendor(orderId, "Outro motivo");

        assertThat(response.status()).isEqualTo(OrderStatus.REJECTED_BY_VENDOR);
        assertThat(response.cancellationReason()).isNull();
        Map<String, Object> order = jdbc.queryForMap("SELECT status, cancellation_reason, rejection_reason FROM orders WHERE id = ?", orderId);
        assertThat(order).containsEntry("status", "REJECTED_BY_VENDOR").containsEntry("rejection_reason", "Antigo");
        assertThat(order.get("cancellation_reason")).isNull();
        assertThat(refundCount()).isEqualTo(1);
        assertThat(inventory()).containsEntry("quantity_available", 100).containsEntry("quantity_reserved", 0);
        verify(auditLogService, never()).log(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(notificationService, never()).create(org.mockito.ArgumentMatchers.any(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void orderCancelledByOpsIsReturnedAsIsOnRejection() {
        salesService.cancel(orderId, "cliente desistiu");
        org.mockito.Mockito.clearInvocations(auditLogService, notificationService);

        OrderResponse response = orderService.rejectByVendor(orderId, "Sem stock");

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.cancellationReason()).isNull();
        Map<String, Object> order = jdbc.queryForMap("SELECT status, cancellation_reason, rejection_reason FROM orders WHERE id = ?", orderId);
        assertThat(order).containsEntry("status", "CANCELLED");
        assertThat(order.get("cancellation_reason")).isNull();
        assertThat(order.get("rejection_reason")).isNull();
        assertThat(refundCount()).isZero(); // an ops cancellation creates no refund; the rejection must not either
        assertStockReleasedOnce();
        verify(auditLogService, never()).log(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(notificationService, never()).create(org.mockito.ArgumentMatchers.any(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void concurrentRejectionAndSalesCancelProduceExactlyOneTerminalOutcomeWithoutDeadlock() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Object> rejection;
        Future<Object> cancellation;
        try {
            rejection = pool.submit(() -> race(start, () -> orderService.rejectByVendor(orderId, "Sem stock")));
            cancellation = pool.submit(() -> race(start, () -> salesService.cancel(orderId, "cliente desistiu")));
            start.countDown();
            Object rejectionOutcome = rejection.get(30, TimeUnit.SECONDS);
            Object cancellationOutcome = cancellation.get(30, TimeUnit.SECONDS);

            // Neither side may fail on locking (deadlock / lock timeout): the payment row is the common lock.
            for (Object outcome : List.of(rejectionOutcome, cancellationOutcome)) {
                assertThat(outcome).isNotInstanceOf(org.springframework.dao.PessimisticLockingFailureException.class);
                assertThat(outcome).isNotInstanceOf(org.springframework.dao.CannotAcquireLockException.class);
                assertThat(outcome).isNotInstanceOf(org.springframework.dao.DeadlockLoserDataAccessException.class);
            }
            // Exactly one terminal outcome: the seller rejection or the ops cancellation, never a blend.
            Map<String, Object> order = jdbc.queryForMap("SELECT status, cancellation_reason, delivery_confirmation_code_display FROM orders WHERE id = ?", orderId);
            assertThat(order).containsEntry("status", "CANCELLED");
            assertThat(order.get("delivery_confirmation_code_display")).isNull();
            if ("SELLER_REJECTED".equals(order.get("cancellation_reason"))) {
                assertThat(rejectionOutcome).isInstanceOfSatisfying(OrderResponse.class, r -> assertThat(r.status()).isEqualTo(OrderStatus.CANCELLED));
                assertThat(cancellationOutcome).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("sale_not_cancellable"));
                assertThat(refundCount()).isEqualTo(1);
                assertThat(refundTotal()).isEqualByComparingTo("100.00");
                verify(auditLogService, times(1)).log(eq(AUDIT_ACTION), anyString(), anyString(), anyString(), anyString());
                verify(auditLogService, never()).log(eq("SALE_CANCELLED"), anyString(), anyString(), anyString(), anyString());
            } else {
                assertThat(order.get("cancellation_reason")).isNull();
                assertThat(cancellationOutcome).isInstanceOfSatisfying(SalesActionResponse.class, r -> assertThat(r.orderStatus()).isEqualTo("CANCELLED"));
                assertThat(rejectionOutcome).isInstanceOfSatisfying(OrderResponse.class, r -> assertThat(r.status()).isEqualTo(OrderStatus.CANCELLED));
                assertThat(refundCount()).isZero();
                verify(auditLogService, never()).log(eq(AUDIT_ACTION), anyString(), anyString(), anyString(), anyString());
                verify(auditLogService, times(1)).log(eq("SALE_CANCELLED"), anyString(), anyString(), anyString(), anyString());
            }
            assertStockReleasedOnce();
        } finally {
            pool.shutdownNow();
        }
    }

    private static Object race(CountDownLatch start, java.util.concurrent.Callable<Object> action) throws Exception {
        authenticate(null, "ROLE_OPS");
        start.await();
        try {
            return action.call();
        } catch (RuntimeException e) {
            return e;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private UUID refund(String amount, String status) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO refunds (id, tenant_id, payment_id, order_id, amount, reason, status, idempotency_key, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'earlier', ?, ?, ?, ?)",
                id, tenantId, paymentId, orderId, new BigDecimal(amount), status, "earlier-" + id, now, now);
        return id;
    }

    private List<Map<String, Object>> newRefunds(UUID... existing) {
        List<UUID> known = List.of(existing);
        return jdbc.queryForList("SELECT id, amount, status, idempotency_key FROM refunds WHERE payment_id = ?", paymentId).stream()
                .filter(r -> !known.contains((UUID) r.get("id")))
                .toList();
    }

    private void assertCancelledBySeller() {
        Map<String, Object> order = jdbc.queryForMap("SELECT status, cancellation_reason, rejection_reason, delivery_confirmation_code_display FROM orders WHERE id = ?", orderId);
        assertThat(order).containsEntry("status", "CANCELLED")
                .containsEntry("cancellation_reason", "SELLER_REJECTED")
                .containsEntry("rejection_reason", "Sem stock");
        assertThat(order.get("delivery_confirmation_code_display")).isNull();
    }

    private void assertStockReleasedOnce() {
        assertThat(jdbc.queryForObject("SELECT stock_status FROM orders WHERE id = ?", String.class, orderId)).isEqualTo("RELEASED");
        assertThat(inventory()).containsEntry("quantity_available", 100).containsEntry("quantity_reserved", 0);
    }

    private Map<String, Object> inventory() {
        return jdbc.queryForMap("SELECT quantity_available, quantity_reserved FROM inventory_items WHERE sku_id = ?", skuId);
    }

    private int refundCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM refunds WHERE payment_id = ? AND status <> 'REJECTED'", Integer.class, paymentId);
    }

    private BigDecimal refundTotal() {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM refunds WHERE payment_id = ? AND status <> 'REJECTED'", BigDecimal.class, paymentId);
    }

    private String paymentStatus() {
        return jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, paymentId);
    }

    private String orderStatus() {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private static void authenticate(UUID vendorClaim, String... roles) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none").subject("user")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (vendorClaim != null) {
            builder.claim("vendor_id", vendorClaim.toString());
        }
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(builder.build(), AuthorityUtils.createAuthorityList(roles)));
    }
}
