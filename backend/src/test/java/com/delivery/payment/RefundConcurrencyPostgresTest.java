package com.delivery.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.delivery.common.PostgresTestContainerConfig;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.payment.config.PaymentMockConfirmationProperties;
import com.delivery.payment.dto.RefundRequest;
import com.delivery.payment.dto.RefundResponse;
import com.delivery.payment.mapper.PaymentMapper;
import com.delivery.payment.service.PaymentService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

/**
 * Real-Postgres concurrency tests for refunds (INC-06, COR-04): concurrent requests against one payment
 * never exceed its amount, the same idempotency key yields exactly one row, and V040's UNIQUE holds.
 * Runs without a wrapping test transaction so each service call commits in its own transaction.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import({PaymentService.class, PaymentMapper.class, PaymentMockConfirmationProperties.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RefundConcurrencyPostgresTest {

    private static final boolean DOCKER_AVAILABLE = DockerClientFactory.instance().isDockerAvailable();
    private static final int THREADS = 10;

    @BeforeAll
    static void requiresDockerOrExternalDatabase() {
        Assumptions.assumeTrue(DOCKER_AVAILABLE || System.getenv("SPRING_DATASOURCE_URL") != null,
                "Skipping refund concurrency Postgres tests: no Docker environment and no SPRING_DATASOURCE_URL override");
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
    private PaymentService paymentService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoBean
    private TenantContext tenantContext;
    @MockitoBean
    private AuditLogService auditLogService;

    private UUID tenantId;
    private UUID orderId;
    private UUID paymentId;

    @BeforeEach
    void seed() {
        tenantId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', 'MZN', ?, ?)",
                tenantId, "Refund Tenant", "refund-" + tenantId, now, now);
        jdbcTemplate.update("""
                INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, status, subtotal, fees, taxes, discounts,
                                    discount_total, total, checkout_idempotency_key, delivery_confirmation_code_hash,
                                    delivery_confirmation_code_display, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'PAYMENT_CONFIRMED', 90, 10, 0, 0, 0, 100, ?, 'hash', '000000', ?, ?)""",
                orderId, tenantId, "ref-" + orderId, UUID.randomUUID(), UUID.randomUUID(), "key-" + orderId, now, now);
        jdbcTemplate.update("INSERT INTO payments (id, tenant_id, order_id, amount, provider, idempotency_key, status, created_at, updated_at) VALUES (?, ?, ?, 100.00, 'LOCAL_MOCK', ?, 'CONFIRMED', ?, ?)",
                paymentId, tenantId, orderId, "pay-" + paymentId, now, now);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
    }

    @AfterEach
    void cleanUp() {
        // Removes only the rows this test seeded (throwaway Testcontainers database).
        jdbcTemplate.update("DELETE FROM refunds WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM payments WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM orders WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", tenantId);
    }

    @Test
    void concurrentRefundsNeverExceedThePaymentAmount() throws Exception {
        List<Outcome> outcomes = runConcurrently(i -> new RefundRequest(new BigDecimal("30.00"), "partial", "concurrent-" + i));

        long succeeded = outcomes.stream().filter(o -> o.response != null).count();
        assertThat(succeeded).isEqualTo(3);
        assertThat(outcomes.stream().filter(o -> o.response == null))
                .allSatisfy(o -> assertThat(o.status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        BigDecimal total = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM refunds WHERE payment_id = ?", BigDecimal.class, paymentId);
        assertThat(total).isEqualByComparingTo("90.00");
    }

    @Test
    void concurrentRequestsWithTheSameKeyCreateExactlyOneRefund() throws Exception {
        List<Outcome> outcomes = runConcurrently(i -> new RefundRequest(new BigDecimal("40.00"), "partial", "same-key"));

        assertThat(outcomes).allSatisfy(o -> assertThat(o.response).isNotNull());
        assertThat(outcomes.stream().map(o -> o.response.id()).distinct()).hasSize(1);
        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refunds WHERE tenant_id = ? AND idempotency_key = 'same-key'", Integer.class, tenantId);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void fullApprovalPersistsOrderRefunded() {
        RefundResponse refund = paymentService.requestRefund(paymentId, new RefundRequest(new BigDecimal("100.00"), "full", "full-key"));
        paymentService.approveRefund(refund.id());

        String status = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
        assertThat(status).isEqualTo("REFUNDED");
    }

    @Test
    void uniqueConstraintRejectsDuplicateTenantIdempotencyKey() {
        String insert = "INSERT INTO refunds (id, tenant_id, payment_id, order_id, amount, reason, status, idempotency_key, created_at, updated_at) VALUES (?, ?, ?, ?, 1, 'dup', 'REQUESTED', 'dup-key', now(), now())";
        jdbcTemplate.update(insert, UUID.randomUUID(), tenantId, paymentId, orderId);

        assertThatThrownBy(() -> jdbcTemplate.update(insert, UUID.randomUUID(), tenantId, paymentId, orderId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private List<Outcome> runConcurrently(java.util.function.IntFunction<RefundRequest> requestFor) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                RefundRequest request = requestFor.apply(i);
                Callable<Outcome> task = () -> {
                    start.await();
                    try {
                        return new Outcome(paymentService.requestRefund(paymentId, request), null);
                    } catch (BusinessException e) {
                        return new Outcome(null, e.getStatus());
                    }
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private record Outcome(RefundResponse response, HttpStatus status) {}
}
