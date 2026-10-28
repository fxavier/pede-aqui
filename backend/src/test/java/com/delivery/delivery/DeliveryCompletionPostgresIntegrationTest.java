package com.delivery.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.delivery.common.PostgresTestContainerConfig;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenancyProperties;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.delivery.config.DeliveryCodeProperties;
import com.delivery.delivery.entity.DeliveryStatus;
import com.delivery.delivery.mapper.DeliveryMapper;
import com.delivery.catalog.service.CatalogAccessGuard;
import com.delivery.inventory.mapper.InventoryMapper;
import com.delivery.inventory.service.InventoryService;
import com.delivery.order.service.OrderStockService;
import com.delivery.delivery.service.DeliveryAccessGuard;
import com.delivery.delivery.service.DeliveryCodeHasher;
import com.delivery.delivery.service.DeliveryService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

/**
 * Exercises delivery completion against real Postgres with committed transactions (INC-02):
 * courier ownership, state guard, persisted failed-attempt counter, lockout, concurrency and
 * PIN minimisation. Each test seeds its own tenant so committed rows never collide.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=validate",
        "app.delivery.code-hmac-secret=test-only-delivery-code-hmac-secret", "app.delivery.max-code-attempts=5"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({DeliveryService.class, DeliveryMapper.class, TenantContext.class, TenancyProperties.class, DeliveryAccessGuard.class,
        DeliveryCodeHasher.class, DeliveryCodeProperties.class, AuditLogService.class,
        OrderStockService.class, InventoryService.class, InventoryMapper.class, CatalogAccessGuard.class,
        com.delivery.vendor.api.MerchantScopeGuard.class, com.delivery.vendor.config.MerchantScopeProperties.class})
class DeliveryCompletionPostgresIntegrationTest {

    private static final boolean DOCKER_AVAILABLE = DockerClientFactory.instance().isDockerAvailable();
    // Test-only codes; never real customer PINs.
    private static final String CODE = "482913";
    private static final String WRONG = "000001";

    @BeforeAll
    static void requiresDockerOrExternalDatabase() {
        Assumptions.assumeTrue(DOCKER_AVAILABLE || System.getenv("SPRING_DATASOURCE_URL") != null,
                "Skipping delivery completion Postgres tests: no Docker environment and no SPRING_DATASOURCE_URL override");
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

    @Autowired
    private DeliveryService service;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DeliveryCodeHasher hasher;

    private UUID tenantId;
    private UUID orderId;
    private UUID deliveryId;
    private String assignedCourierUser;
    private String otherCourierUser;

    @BeforeEach
    void seed() {
        tenantId = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', 'MZN', ?, ?)",
                tenantId, "Tenant " + tenantId, "slug-" + tenantId, now(), now());
        assignedCourierUser = "kc-courier-a-" + tenantId;
        otherCourierUser = "kc-courier-b-" + tenantId;
        UUID assignedCourierId = insertCourier(assignedCourierUser);
        insertCourier(otherCourierUser);
        orderId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, status, subtotal, fees, taxes, discounts,
                                    total, checkout_idempotency_key, delivery_confirmation_code_hash,
                                    delivery_confirmation_code_display, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'ASSIGNED_TO_COURIER', 10, 0, 0, 0, 10, ?, ?, ?, ?, ?)""",
                orderId, tenantId, "ref-" + orderId, UUID.randomUUID(), UUID.randomUUID(), "key-" + orderId, hasher.hash(CODE), CODE, now(), now());
        deliveryId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO deliveries (id, tenant_id, order_id, courier_id, status, confirmation_code_hash, confirmation_attempts,
                                        created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ARRIVED_AT_CUSTOMER', ?, 0, ?, ?)""",
                deliveryId, tenantId, orderId, assignedCourierId, hasher.hash(CODE), now(), now());
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void assignedCourierCompletesWithCorrectCodeAndPlainPinIsCleared() {
        authenticate(assignedCourierUser, "COURIER");

        assertThat(service.complete(deliveryId, CODE).status()).isEqualTo(DeliveryStatus.DELIVERED);

        assertThat(deliveryStatus()).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId)).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForObject("SELECT delivery_confirmation_code_display FROM orders WHERE id = ?", String.class, orderId)).isNull();
    }

    @Test
    void courierAppFlowCompletesFromAcceptedState() {
        // The courier app accepts the job and posts /complete directly (it never PATCHes intermediate statuses).
        jdbc.update("UPDATE deliveries SET status = 'ACCEPTED' WHERE id = ?", deliveryId);
        authenticate(assignedCourierUser, "COURIER");

        assertThat(service.complete(deliveryId, CODE).status()).isEqualTo(DeliveryStatus.DELIVERED);
        // INC-07 x INC-02: no PICKED_UP PATCH happened, so completion itself closes the stock lifecycle.
        assertThat(jdbc.queryForObject("SELECT stock_status FROM orders WHERE id = ?", String.class, orderId))
                .isEqualTo("COMMITTED");
    }

    @Test
    void wrongCodePersistsAttemptCounterAndEventButNothingElse() {
        authenticate(assignedCourierUser, "COURIER");

        assertThatThrownBy(() -> service.complete(deliveryId, WRONG)).isInstanceOf(BusinessException.class);

        assertThat(attempts()).isEqualTo(1);
        assertThat(events("CONFIRMATION_FAILED")).isEqualTo(1);
        assertThat(deliveryStatus()).isEqualTo("ARRIVED_AT_CUSTOMER");
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId)).isEqualTo("ASSIGNED_TO_COURIER");
    }

    @Test
    void fifthWrongCodeLocksAndCorrectCodeIsThenRejected() {
        authenticate(assignedCourierUser, "COURIER");
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> service.complete(deliveryId, WRONG)).isInstanceOf(BusinessException.class);
        }
        assertThatThrownBy(() -> service.complete(deliveryId, WRONG))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.LOCKED);
                    assertThat(ex.getCode()).isEqualTo("delivery_code_locked");
                });
        assertThat(attempts()).isEqualTo(5);
        assertThat(events("CONFIRMATION_LOCKED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE tenant_id = ? AND action = 'DELIVERY_CODE_LOCKED'", Integer.class, tenantId)).isEqualTo(1);

        assertThatThrownBy(() -> service.complete(deliveryId, CODE))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.LOCKED));
        assertThat(deliveryStatus()).isEqualTo("ARRIVED_AT_CUSTOMER");
        assertThat(attempts()).isEqualTo(5);
    }

    @Test
    void unassignedCourierCannotCompleteOrUpdateStatus() {
        authenticate(otherCourierUser, "COURIER");

        assertThatThrownBy(() -> service.complete(deliveryId, CODE))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.updateStatus(deliveryId, DeliveryStatus.FAILED_DELIVERY, null, null))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        assertThat(deliveryStatus()).isEqualTo("ARRIVED_AT_CUSTOMER");
        assertThat(attempts()).isZero();
    }

    @Test
    void opsCannotCompleteWithPinOnBehalfOfCourier() {
        authenticate("kc-ops-" + tenantId, "OPS");

        assertThatThrownBy(() -> service.complete(deliveryId, CODE))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(deliveryStatus()).isEqualTo("ARRIVED_AT_CUSTOMER");
    }

    @Test
    void opsStatusUpdateIsAllowedAndAudited() {
        authenticate("kc-ops-" + tenantId, "OPS");

        assertThat(service.updateStatus(deliveryId, DeliveryStatus.FAILED_DELIVERY, null, null).status())
                .isEqualTo(DeliveryStatus.FAILED_DELIVERY);

        // G: FAILED_DELIVERY keeps cancelling the order (no refund/stock here), and the plain PIN is cleared.
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT delivery_confirmation_code_display FROM orders WHERE id = ?", String.class, orderId)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE tenant_id = ? AND action = 'DELIVERY_STATUS_UPDATED'", Integer.class, tenantId)).isEqualTo(1);
    }

    @Test
    void completionFromNonCompletableStateIsConflictWithoutEffects() {
        jdbc.update("UPDATE deliveries SET status = 'ASSIGNED' WHERE id = ?", deliveryId);
        authenticate(assignedCourierUser, "COURIER");

        assertThatThrownBy(() -> service.complete(deliveryId, CODE))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(deliveryStatus()).isEqualTo("ASSIGNED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_events WHERE delivery_id = ?", Integer.class, deliveryId)).isZero();
    }

    @Test
    void doubleCompletionIsConflictWithoutNewEvent() {
        authenticate(assignedCourierUser, "COURIER");
        service.complete(deliveryId, CODE);
        int eventsAfterFirst = jdbc.queryForObject("SELECT COUNT(*) FROM delivery_events WHERE delivery_id = ?", Integer.class, deliveryId);

        assertThatThrownBy(() -> service.complete(deliveryId, CODE))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_events WHERE delivery_id = ?", Integer.class, deliveryId)).isEqualTo(eventsAfterFirst);
    }

    @Test
    void statusEndpointCannotMarkDeliveredWithoutPin() {
        authenticate(assignedCourierUser, "COURIER");

        assertThatThrownBy(() -> service.updateStatus(deliveryId, DeliveryStatus.DELIVERED, null, null))
                .isInstanceOf(BusinessException.class);
        assertThat(deliveryStatus()).isEqualTo("ARRIVED_AT_CUSTOMER");
    }

    @Test
    void legacySha256DeliveriesStillValidate() {
        // V038 flags every pre-existing row legacy; simulate one created by the old checkout.
        jdbc.update("UPDATE deliveries SET confirmation_code_hash = ?, confirmation_code_legacy = TRUE WHERE id = ?", legacySha256(CODE), deliveryId);
        authenticate(assignedCourierUser, "COURIER");

        assertThat(service.complete(deliveryId, CODE).status()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void sha256HashIsRejectedForNonLegacyRows() {
        jdbc.update("UPDATE deliveries SET confirmation_code_hash = ?, confirmation_code_legacy = FALSE WHERE id = ?", legacySha256(CODE), deliveryId);
        authenticate(assignedCourierUser, "COURIER");

        assertThatThrownBy(() -> service.complete(deliveryId, CODE))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("invalid_delivery_code"));
    }

    @Test
    void newDeliveriesDefaultToNonLegacy() {
        assertThat(jdbc.queryForObject("SELECT confirmation_code_legacy FROM deliveries WHERE id = ?", Boolean.class, deliveryId)).isFalse();
    }

    @Test
    void opsUnlockResetsAttemptsAndCourierCanThenComplete() {
        jdbc.update("UPDATE deliveries SET confirmation_attempts = 5 WHERE id = ?", deliveryId);
        authenticate("kc-ops-" + tenantId, "OPS");

        assertThat(service.unlockCode(deliveryId).confirmationAttempts()).isZero();
        assertThat(attempts()).isZero();
        assertThat(events("CONFIRMATION_UNLOCKED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE tenant_id = ? AND action = 'DELIVERY_CODE_UNLOCKED'", Integer.class, tenantId)).isEqualTo(1);

        authenticate(assignedCourierUser, "COURIER");
        assertThat(service.complete(deliveryId, CODE).status()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void pinNeverAppearsInLogs() {
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            authenticate(assignedCourierUser, "COURIER");
            assertThatThrownBy(() -> service.complete(deliveryId, WRONG)).isInstanceOf(BusinessException.class);
            service.complete(deliveryId, CODE);
        } finally {
            root.detachAppender(appender);
        }
        assertThat(appender.list).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain(CODE).doesNotContain(WRONG);
        });
        assertThat(jdbc.queryForList("SELECT notes FROM delivery_events WHERE delivery_id = ?", String.class, deliveryId))
                .allSatisfy(notes -> assertThat(notes).doesNotContain(CODE).doesNotContain(WRONG));
    }

    @Test
    void concurrentWrongAttemptsNeverLoseIncrements() throws Exception {
        int threads = 4;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<String>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                Callable<String> attempt = () -> {
                    authenticate(assignedCourierUser, "COURIER");
                    start.await();
                    try {
                        service.complete(deliveryId, WRONG);
                        return "unexpected-success";
                    } catch (BusinessException ex) {
                        return "counted"; // committed failed attempt (noRollbackFor path)
                    } catch (RuntimeException ex) {
                        return "conflict"; // optimistic-lock loser: rolled back, nothing verified-and-counted
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                };
                results.add(pool.submit(attempt));
            }
            start.countDown();
            int counted = 0;
            for (Future<String> result : results) {
                String outcome = result.get(30, TimeUnit.SECONDS);
                assertThat(outcome).isNotEqualTo("unexpected-success");
                if (outcome.equals("counted")) {
                    counted++;
                }
            }
            // The @Version column serialises the increments: every committed failure is reflected exactly once.
            assertThat(counted).isGreaterThanOrEqualTo(1);
            assertThat(attempts()).isEqualTo(counted);
            assertThat(events("CONFIRMATION_FAILED")).isEqualTo(counted);
        } finally {
            pool.shutdownNow();
        }
    }

    // --- Order no longer deliverable: 409 order_not_deliverable, nothing persisted ---

    @Test
    void refundedOrderCannotBeCompletedOrProgressedByCourierOrOps() {
        // Refund after pickup: the delivery is left in place (goods out), but the order is REFUNDED.
        jdbc.update("UPDATE orders SET status = 'REFUNDED', delivery_confirmation_code_display = NULL WHERE id = ?", orderId);
        authenticate(assignedCourierUser, "COURIER");

        for (String pin : List.of(CODE, WRONG)) {
            assertThatThrownBy(() -> service.complete(deliveryId, pin)).isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(ex.getCode()).isEqualTo("order_not_deliverable");
            });
        }
        assertThatThrownBy(() -> service.updateStatus(deliveryId, DeliveryStatus.FAILED_DELIVERY, null, null))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("order_not_deliverable"));
        authenticate("kc-ops-" + tenantId, "OPS");
        assertThatThrownBy(() -> service.updateStatus(deliveryId, DeliveryStatus.FAILED_DELIVERY, null, null))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("order_not_deliverable"));

        assertThat(deliveryStatus()).isEqualTo("ARRIVED_AT_CUSTOMER");
        assertThat(attempts()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_events WHERE delivery_id = ?", Integer.class, deliveryId)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId)).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("SELECT stock_status FROM orders WHERE id = ?", String.class, orderId)).isEqualTo("RESERVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE tenant_id = ?", Integer.class, tenantId)).isZero();
    }

    @Test
    void sellerRejectedOrderWithAnUnretiredDeliveryCannotBeCompletedOrPickedUp() {
        // A row cancelled before deliveries were retired (or by any path that left the delivery live).
        jdbc.update("UPDATE orders SET status = 'CANCELLED', cancellation_reason = 'SELLER_REJECTED', rejection_reason = 'Sem stock' WHERE id = ?", orderId);
        jdbc.update("UPDATE deliveries SET status = 'ACCEPTED' WHERE id = ?", deliveryId);
        authenticate(assignedCourierUser, "COURIER");

        assertThatThrownBy(() -> service.complete(deliveryId, CODE))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("order_not_deliverable"));
        assertThatThrownBy(() -> service.updateStatus(deliveryId, DeliveryStatus.ARRIVED_AT_VENDOR, null, null))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("order_not_deliverable"));

        assertThat(deliveryStatus()).isEqualTo("ACCEPTED");
        assertThat(attempts()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_events WHERE delivery_id = ?", Integer.class, deliveryId)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId)).isEqualTo("CANCELLED");
    }

    // --- helpers ---

    private UUID insertCourier(String keycloakUserId) {
        UUID profileId = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)",
                profileId, tenantId, keycloakUserId, keycloakUserId + "@test.local", "Courier", now(), now());
        UUID courierId = UUID.randomUUID();
        jdbc.update("INSERT INTO couriers (id, tenant_id, user_profile_id, verification_status, available, created_at, updated_at) VALUES (?, ?, ?, 'APPROVED', TRUE, ?, ?)",
                courierId, tenantId, profileId, now(), now());
        return courierId;
    }

    private void authenticate(String keycloakUserId, String role) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none")
                .subject(keycloakUserId)
                .claim(TenantContext.TENANT_ID_CLAIM, tenantId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private int attempts() {
        return jdbc.queryForObject("SELECT confirmation_attempts FROM deliveries WHERE id = ?", Integer.class, deliveryId);
    }

    private String deliveryStatus() {
        return jdbc.queryForObject("SELECT status FROM deliveries WHERE id = ?", String.class, deliveryId);
    }

    private int events(String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM delivery_events WHERE delivery_id = ? AND event_type = ?", Integer.class, deliveryId, type);
    }

    /** Unsalted SHA-256 as stored for deliveries created before INC-02. */
    private static String legacySha256(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Timestamp now() {
        return Timestamp.from(Instant.now());
    }
}
