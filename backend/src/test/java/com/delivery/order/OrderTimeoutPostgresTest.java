package com.delivery.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.PostgresApiE2ETestBase;
import com.delivery.common.TestParents;
import com.delivery.common.security.TenancyProperties;
import com.delivery.order.job.AcceptanceTimeoutJob;
import com.delivery.order.job.PaymentExpiryJob;
import com.delivery.order.service.OrderTimeoutService;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W4-A (LAC-25/LAC-15) on real Postgres, shared full context: the payment-expiry and acceptance-timeout jobs cancel
 * overdue orders with all their effects exactly once (state machine history + event, payment status + history, stock,
 * delivery retirement, refund, notification, audit), skip an order whose payment row a request holds, and never double
 * an effect when a vendor rejects/accepts the same order concurrently. Also: the history tables are append-only and
 * readable through {@code GET /orders/{id}/history} with the documented scoping.
 */
class OrderTimeoutPostgresTest extends PostgresApiE2ETestBase {
    /** Older than every other test's seed, so these orders are first in the oldest-first job batches. */
    private static final Instant LONG_AGO = Instant.now().minus(Duration.ofDays(400));

    @Autowired private OrderTimeoutService timeoutService;
    @Autowired private PaymentExpiryJob paymentExpiryJob;
    @Autowired private AcceptanceTimeoutJob acceptanceTimeoutJob;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;
    @Autowired private TenancyProperties tenancyProperties;
    @Autowired private MeterRegistry meterRegistry;
    @Autowired private PlatformTransactionManager transactionManager;

    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
    private UUID tenantId;
    private UUID vendorId;
    private UUID categoryId;
    private String merchant;

    @BeforeEach
    void seed() {
        tenantId = tenancyProperties.getOperatorTenantId();
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            Jwt jwt = tokens.get(invocation.<String>getArgument(0));
            if (jwt == null) throw new BadJwtException("unknown test token");
            return jwt;
        });
        Timestamp now = Timestamp.from(Instant.now());
        vendorId = TestParents.vendor(jdbc, tenantId);
        categoryId = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, tenant_id, name, slug, active, created_at, updated_at) VALUES (?, ?, 'C', ?, TRUE, ?, ?)",
                categoryId, tenantId, "slug-" + categoryId, now, now);
        merchant = merchantOf(vendorId);
    }

    // --- payment expiry -------------------------------------------------------------------------

    @Test
    void paymentExpiryCancelsTheOverdueOrderWithAllItsEffectsOnce() {
        Seeded overdue = seedOrder("PAYMENT_PENDING", "PENDING_CONFIRMATION", LONG_AGO, null);
        Seeded fresh = seedOrder("PAYMENT_PENDING", "PENDING_CONFIRMATION", Instant.now(), null);
        // Inconsistent row (payment captured, order still pending): never cancelled without a refund decision.
        Seeded inconsistent = seedOrder("PAYMENT_PENDING", "CONFIRMED", LONG_AGO, LONG_AGO);

        paymentExpiryJob.run(); // through JobRunner: MDC job name becomes the history actor

        assertThat(order(overdue)).containsEntry("status", "CANCELLED").containsEntry("cancellation_reason", "PAYMENT_EXPIRED")
                .containsEntry("stock_status", "RELEASED");
        assertThat(order(overdue).get("delivery_confirmation_code_display")).isNull();
        assertThat(paymentStatus(overdue)).isEqualTo("CANCELLED");
        assertThat(deliveryStatus(overdue)).isEqualTo("CANCELLED");
        assertThat(inventory(overdue)).containsEntry("quantity_available", 100).containsEntry("quantity_reserved", 0);
        assertThat(refunds(overdue)).isZero();
        assertThat(orderHistory(overdue)).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("from_status", "PAYMENT_PENDING").containsEntry("to_status", "CANCELLED")
                    .containsEntry("actor_type", "SYSTEM").containsEntry("actor_id", PaymentExpiryJob.NAME)
                    .containsEntry("reason", "PAYMENT_EXPIRED");
            assertThat(row.get("correlation_id")).isNotNull(); // the job run id
        });
        assertThat(paymentHistory(overdue)).singleElement().satisfies(row ->
                assertThat(row).containsEntry("from_status", "PENDING_CONFIRMATION").containsEntry("to_status", "CANCELLED"));
        assertThat(audits(overdue, "ORDER_CANCELLED_PAYMENT_EXPIRED")).isEqualTo(1);
        assertThat(statusChangedPublications(overdue)).isGreaterThanOrEqualTo(1);
        assertThat(order(fresh)).containsEntry("status", "PAYMENT_PENDING").containsEntry("stock_status", "RESERVED");
        assertThat(order(inconsistent)).containsEntry("status", "PAYMENT_PENDING").containsEntry("stock_status", "RESERVED");
        assertThat(orderHistory(inconsistent)).isEmpty();
        assertThat(meterRegistry.find("order.timeout.orders").tags("job", PaymentExpiryJob.NAME, "outcome", "cancelled").counter())
                .isNotNull();

        // Idempotent: a second run finds nothing to do for this order.
        timeoutService.expirePendingPayments();
        assertThat(orderHistory(overdue)).hasSize(1);
        assertThat(paymentHistory(overdue)).hasSize(1);
        assertThat(inventory(overdue)).containsEntry("quantity_available", 100).containsEntry("quantity_reserved", 0);
        assertThat(audits(overdue, "ORDER_CANCELLED_PAYMENT_EXPIRED")).isEqualTo(1);
    }

    @Test
    void anOrderWhosePaymentIsLockedByARequestIsSkippedAndExpiredOnTheNextRun() throws Exception {
        Seeded overdue = seedOrder("PAYMENT_PENDING", "PENDING_CONFIRMATION", LONG_AGO, null);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.queryForList("SELECT id FROM payments WHERE id = ? FOR UPDATE", overdue.paymentId());
            locked.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        holder.start();
        try {
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            OrderTimeoutService.Result busy = timeoutService.expirePendingPayments();
            assertThat(busy.skippedBusy()).isGreaterThanOrEqualTo(1);
            assertThat(order(overdue)).containsEntry("status", "PAYMENT_PENDING");
        } finally {
            release.countDown();
            holder.join(10_000);
        }

        timeoutService.expirePendingPayments();
        assertThat(order(overdue)).containsEntry("status", "CANCELLED").containsEntry("cancellation_reason", "PAYMENT_EXPIRED");
    }

    // --- acceptance timeout ---------------------------------------------------------------------

    @Test
    void acceptanceTimeoutCancelsRefundsReleasesAndNotifiesOnce() {
        Seeded overdue = seedOrder("PAYMENT_CONFIRMED", "CONFIRMED", LONG_AGO, LONG_AGO);
        Seeded fresh = seedOrder("PAYMENT_CONFIRMED", "CONFIRMED", LONG_AGO, Instant.now()); // paid just now
        // Inconsistent row (order confirmed, payment never captured): no refund of uncaptured money.
        Seeded uncaptured = seedOrder("PAYMENT_CONFIRMED", "PENDING_CONFIRMATION", LONG_AGO, null);

        acceptanceTimeoutJob.run();

        assertThat(order(overdue)).containsEntry("status", "CANCELLED").containsEntry("cancellation_reason", "VENDOR_TIMEOUT")
                .containsEntry("stock_status", "RELEASED");
        assertThat(deliveryStatus(overdue)).isEqualTo("CANCELLED");
        assertThat(inventory(overdue)).containsEntry("quantity_available", 100).containsEntry("quantity_reserved", 0);
        assertThat(jdbc.queryForList("SELECT amount, status, idempotency_key FROM refunds WHERE order_id = ?", overdue.orderId()))
                .singleElement().satisfies(row -> {
                    assertThat(row.get("amount").toString()).isEqualTo("100.00");
                    assertThat(row).containsEntry("status", "REQUESTED").containsEntry("idempotency_key", "vendor-timeout-" + overdue.orderId());
                });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE business_reference = ? AND type = 'order_cancelled_vendor_timeout'",
                Integer.class, "ref-" + overdue.orderId())).isEqualTo(1);
        assertThat(audits(overdue, "ORDER_CANCELLED_VENDOR_TIMEOUT")).isEqualTo(1);
        assertThat(orderHistory(overdue)).singleElement().satisfies(row ->
                assertThat(row).containsEntry("from_status", "PAYMENT_CONFIRMED").containsEntry("to_status", "CANCELLED")
                        .containsEntry("reason", "VENDOR_TIMEOUT").containsEntry("actor_id", AcceptanceTimeoutJob.NAME));
        assertThat(order(fresh)).containsEntry("status", "PAYMENT_CONFIRMED").containsEntry("stock_status", "RESERVED");
        assertThat(refunds(fresh)).isZero();
        assertThat(order(uncaptured)).containsEntry("status", "PAYMENT_CONFIRMED");
        assertThat(refunds(uncaptured)).isZero();

        timeoutService.cancelUnacceptedOrders();
        assertThat(refunds(overdue)).isEqualTo(1);
        assertThat(orderHistory(overdue)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE business_reference = ?", Integer.class,
                "ref-" + overdue.orderId())).isEqualTo(1);
    }

    @Test
    void acceptanceTimeoutRacingASellerRejectionCancelsAndRefundsExactlyOnce() throws Exception {
        for (int round = 0; round < 4; round++) {
            Seeded seeded = seedOrder("PAYMENT_CONFIRMED", "CONFIRMED", LONG_AGO, LONG_AGO);
            String token = merchantToken();

            race(() -> {
                mockMvc.perform(patch("/api/v1/vendor/orders/{id}/reject", seeded.orderId())
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Sem stock\"}"))
                        .andExpect(status().isOk());
                return null;
            }, () -> timeoutService.cancelUnacceptedOrders());
            // The job may have skipped the order (rejection held the payment lock) or lost it: let it settle.
            timeoutService.cancelUnacceptedOrders();

            assertThat(order(seeded)).containsEntry("status", "CANCELLED").containsEntry("stock_status", "RELEASED");
            assertThat(order(seeded).get("cancellation_reason")).isIn("SELLER_REJECTED", "VENDOR_TIMEOUT");
            assertThat(refunds(seeded)).as("round " + round).isEqualTo(1);
            assertThat(inventory(seeded)).containsEntry("quantity_available", 100).containsEntry("quantity_reserved", 0);
            assertThat(orderHistory(seeded)).as("round " + round).singleElement()
                    .satisfies(row -> assertThat(row).containsEntry("to_status", "CANCELLED"));
            assertThat(audits(seeded, "ORDER_CANCELLED_SELLER_REJECTED") + audits(seeded, "ORDER_CANCELLED_VENDOR_TIMEOUT")).isEqualTo(1);
        }
    }

    @Test
    void acceptanceTimeoutRacingAVendorAcceptHasOneWinnerWithoutDoubleEffects() throws Exception {
        for (int round = 0; round < 4; round++) {
            Seeded seeded = seedOrder("PAYMENT_CONFIRMED", "CONFIRMED", LONG_AGO, LONG_AGO);
            String token = merchantToken();

            race(() -> {
                int code = mockMvc.perform(patch("/api/v1/vendor/orders/{id}/accept", seeded.orderId())
                        .header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus();
                assertThat(code).isIn(200, 409); // 409: invalid_order_transition / concurrent_modification
                return null;
            }, () -> timeoutService.cancelUnacceptedOrders());
            timeoutService.cancelUnacceptedOrders();

            String status = (String) order(seeded).get("status");
            assertThat(status).as("round " + round).isIn("ACCEPTED_BY_VENDOR", "CANCELLED");
            if (status.equals("ACCEPTED_BY_VENDOR")) {
                assertThat(refunds(seeded)).isZero();
                assertThat(order(seeded)).containsEntry("stock_status", "RESERVED");
                assertThat(inventory(seeded)).containsEntry("quantity_available", 95).containsEntry("quantity_reserved", 5);
            } else {
                assertThat(order(seeded)).containsEntry("cancellation_reason", "VENDOR_TIMEOUT").containsEntry("stock_status", "RELEASED");
                assertThat(refunds(seeded)).isEqualTo(1);
                assertThat(inventory(seeded)).containsEntry("quantity_available", 100).containsEntry("quantity_reserved", 0);
            }
            assertThat(orderHistory(seeded)).as("round " + round).singleElement()
                    .satisfies(row -> assertThat(row).containsEntry("from_status", "PAYMENT_CONFIRMED").containsEntry("to_status", status));
        }
    }

    // --- history: append-only + read endpoint ---------------------------------------------------

    @Test
    void historyTablesAreAppendOnlyButFollowTheirOrderOnDelete() {
        Seeded seeded = seedOrder("PAYMENT_PENDING", "PENDING_CONFIRMATION", LONG_AGO, null);
        timeoutService.expirePendingPayments();
        UUID rowId = (UUID) jdbc.queryForObject("SELECT id FROM order_status_history WHERE order_id = ?", Object.class, seeded.orderId());

        assertThatThrownBy(() -> jdbc.update("UPDATE order_status_history SET reason = 'x' WHERE id = ?", rowId))
                .isInstanceOf(DataAccessException.class).satisfies(OrderTimeoutPostgresTest::isAppendOnlyRejection);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM order_status_history WHERE id = ?", rowId))
                .isInstanceOf(DataAccessException.class).satisfies(OrderTimeoutPostgresTest::isAppendOnlyRejection);
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_status_history SET reason = 'x' WHERE order_id = ?", seeded.orderId()))
                .isInstanceOf(DataAccessException.class).satisfies(OrderTimeoutPostgresTest::isAppendOnlyRejection);
        assertThat(orderHistory(seeded)).hasSize(1);

        // A controlled purge is possible inside a transaction that opts in.
        Integer purged = new TransactionTemplate(transactionManager).execute(status -> {
            jdbc.execute("SET LOCAL app.status_history_purge = 'on'");
            return jdbc.update("DELETE FROM payment_status_history WHERE order_id = ?", seeded.orderId());
        });
        assertThat(purged).isEqualTo(1);

        // Deleting the order (retention, test clean-up) cascades to its history.
        jdbc.update("DELETE FROM refunds WHERE order_id = ?", seeded.orderId());
        jdbc.update("DELETE FROM deliveries WHERE order_id = ?", seeded.orderId());
        jdbc.update("DELETE FROM payments WHERE order_id = ?", seeded.orderId());
        jdbc.update("DELETE FROM order_items WHERE order_id = ?", seeded.orderId());
        jdbc.update("DELETE FROM orders WHERE id = ?", seeded.orderId());
        assertThat(orderHistory(seeded)).isEmpty();
    }

    @Test
    void historyEndpointIsScopedToStaffAndTheOwningMerchant() throws Exception {
        Seeded seeded = seedOrder("PAYMENT_CONFIRMED", "CONFIRMED", LONG_AGO, LONG_AGO);
        timeoutService.cancelUnacceptedOrders();

        mockMvc.perform(get("/api/v1/orders/{id}/history", seeded.orderId()).header("Authorization", "Bearer " + staffToken("OPS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.orderStatus[0].fromStatus").value("PAYMENT_CONFIRMED"))
                .andExpect(jsonPath("$.orderStatus[0].toStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.orderStatus[0].reason").value("VENDOR_TIMEOUT"))
                .andExpect(jsonPath("$.orderStatus[0].actorType").value("SYSTEM"));
        mockMvc.perform(get("/api/v1/orders/{id}/history", seeded.orderId()).header("Authorization", "Bearer " + staffToken("SUPPORT")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/orders/{id}/history", seeded.orderId()).header("Authorization", "Bearer " + merchantToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus[0].actorId").doesNotExist());
        String otherMerchant = token(merchantOf(TestParents.vendor(jdbc, tenantId)), "VENDOR_ADMIN");
        mockMvc.perform(get("/api/v1/orders/{id}/history", seeded.orderId()).header("Authorization", "Bearer " + otherMerchant))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/orders/{id}/history", seeded.orderId()).header("Authorization", "Bearer " + staffToken("FINANCE")))
                .andExpect(status().isForbidden());
    }

    // --- helpers ---------------------------------------------------------------------------------

    private static void isAppendOnlyRejection(Throwable error) {
        assertThat(org.springframework.core.NestedExceptionUtils.getMostSpecificCause(error).getMessage()).contains("append-only");
    }

    private record Seeded(UUID orderId, UUID inventoryId, UUID paymentId) {
    }

    @FunctionalInterface
    private interface Action {
        Object call() throws Exception;
    }

    /** Starts both actions at the same instant (barrier) and propagates any assertion failure from either side. */
    private void race(Action first, Action second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Action action : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return action.call();
                }));
            }
            for (Future<Object> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * One order of 5 units reserved at checkout (inventory 95 available + 5 reserved), its payment (confirmed at
     * {@code confirmedAt} when given) and a not-yet-dispatched delivery.
     */
    private Seeded seedOrder(String orderStatus, String paymentStatus, Instant createdAt, Instant confirmedAt) {
        Timestamp now = Timestamp.from(Instant.now());
        Timestamp created = Timestamp.from(createdAt);
        UUID productId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID inventoryId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO products (id, tenant_id, vendor_id, category_id, name, status,
                                      requires_prescription_metadata, manual_validation_required, prohibited_fuel, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Arroz', 'ACTIVE', FALSE, FALSE, FALSE, ?, ?)""",
                productId, tenantId, vendorId, categoryId, now, now);
        jdbc.update("INSERT INTO skus (id, tenant_id, product_id, sku_code, name, price, active, created_at, updated_at) VALUES (?, ?, ?, ?, 'Arroz 1kg', 18.00, TRUE, ?, ?)",
                skuId, tenantId, productId, "sku-" + skuId, now, now);
        jdbc.update("INSERT INTO inventory_items (id, tenant_id, vendor_id, sku_id, quantity_available, quantity_reserved, status, created_at, updated_at, version) VALUES (?, ?, ?, ?, 95, 5, 'ACTIVE', ?, ?, 0)",
                inventoryId, tenantId, vendorId, skuId, now, now);
        jdbc.update("""
                INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, status, subtotal, fees, taxes, discounts,
                                    discount_total, total, checkout_idempotency_key, delivery_confirmation_code_hash,
                                    delivery_confirmation_code_display, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 90, 10, 0, 0, 0, 100, ?, 'hash', '000000', ?, ?)""",
                orderId, tenantId, "ref-" + orderId, TestParents.customer(jdbc, tenantId), vendorId, orderStatus, "key-" + orderId, created, created);
        jdbc.update("INSERT INTO order_items (id, order_id, tenant_id, sku_id, product_name_snapshot, sku_name_snapshot, unit_price_snapshot, quantity, line_total, created_at) VALUES (?, ?, ?, ?, 'Arroz', 'Arroz 1kg', 18.00, 5, 90.00, ?)",
                UUID.randomUUID(), orderId, tenantId, skuId, now);
        jdbc.update("INSERT INTO payments (id, tenant_id, order_id, amount, provider, idempotency_key, status, confirmed_at, created_at, updated_at) VALUES (?, ?, ?, 100.00, 'LOCAL_MOCK', ?, ?, ?, ?, ?)",
                paymentId, tenantId, orderId, "pay-" + paymentId, paymentStatus, confirmedAt == null ? null : Timestamp.from(confirmedAt), created, created);
        jdbc.update("INSERT INTO deliveries (id, tenant_id, order_id, courier_id, status, confirmation_code_hash, confirmation_attempts, created_at, updated_at) VALUES (?, ?, ?, NULL, 'DISPATCH_PENDING', 'hash', 0, ?, ?)",
                UUID.randomUUID(), tenantId, orderId, now, now);
        return new Seeded(orderId, inventoryId, paymentId);
    }

    private Map<String, Object> order(Seeded seeded) {
        return jdbc.queryForMap("SELECT status, cancellation_reason, stock_status, delivery_confirmation_code_display FROM orders WHERE id = ?", seeded.orderId());
    }

    private String paymentStatus(Seeded seeded) {
        return jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, seeded.paymentId());
    }

    private String deliveryStatus(Seeded seeded) {
        return jdbc.queryForObject("SELECT status FROM deliveries WHERE order_id = ?", String.class, seeded.orderId());
    }

    private Map<String, Object> inventory(Seeded seeded) {
        return jdbc.queryForMap("SELECT quantity_available, quantity_reserved FROM inventory_items WHERE id = ?", seeded.inventoryId());
    }

    private int refunds(Seeded seeded) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM refunds WHERE order_id = ?", Integer.class, seeded.orderId());
    }

    private List<Map<String, Object>> orderHistory(Seeded seeded) {
        return jdbc.queryForList("SELECT from_status, to_status, actor_type, actor_id, reason, correlation_id FROM order_status_history WHERE order_id = ? ORDER BY created_at, id",
                seeded.orderId());
    }

    private List<Map<String, Object>> paymentHistory(Seeded seeded) {
        return jdbc.queryForList("SELECT from_status, to_status FROM payment_status_history WHERE order_id = ? ORDER BY created_at, id", seeded.orderId());
    }

    private int audits(Seeded seeded, String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE target_id = ? AND action = ?", Integer.class,
                seeded.orderId().toString(), action);
    }

    private int statusChangedPublications(Seeded seeded) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM event_publication WHERE event_type = ? AND serialized_event LIKE ?", Integer.class,
                OrderStatusChanged.class.getName(), "%" + seeded.orderId() + "%");
    }

    /** A VENDOR_ADMIN profile linked to {@code vendor}; returns its Keycloak subject. */
    private String merchantOf(UUID vendor) {
        Timestamp now = Timestamp.from(Instant.now());
        String subject = "kc-" + UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'Merchant', 'ACTIVE', ?, ?)",
                profileId, tenantId, subject, subject + "@example.test", now, now);
        jdbc.update("INSERT INTO app_user_profile_roles (app_user_profile_id, role) VALUES (?, 'VENDOR_ADMIN')", profileId);
        jdbc.update("INSERT INTO merchant_users (id, tenant_id, vendor_id, app_user_profile_id, role) VALUES (?, ?, ?, ?, 'OWNER')",
                UUID.randomUUID(), tenantId, vendor, profileId);
        return subject;
    }

    private String merchantToken() {
        return token(merchant, "VENDOR_ADMIN");
    }

    private String staffToken(String role) {
        return token("kc-staff-" + UUID.randomUUID(), role);
    }

    private String token(String subject, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        claims.put("azp", "delivery-app");
        claims.put("tenant_id", tenantId.toString());
        claims.put("realm_access", Map.of("roles", List.of(role)));
        String value = "timeout-token-" + UUID.randomUUID();
        tokens.put(value, new Jwt(value, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "RS256"), claims));
        return value;
    }
}
