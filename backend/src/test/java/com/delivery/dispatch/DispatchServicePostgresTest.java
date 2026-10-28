package com.delivery.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.common.PostgresTestContainerConfig;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.delivery.repository.DeliveryEventRepository;
import com.delivery.delivery.repository.DeliveryRepository;
import com.delivery.dispatch.dto.DispatchJobResponse;
import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.mapper.DispatchMapper;
import com.delivery.dispatch.repository.CourierRepository;
import com.delivery.dispatch.repository.DispatchJobRepository;
import com.delivery.dispatch.service.CourierService;
import com.delivery.dispatch.service.DispatchService;
import com.delivery.order.repository.OrderRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;

/**
 * Real-Postgres coverage of dispatch ownership, offer expiry, atomic acceptance, assignment guards,
 * the V035 exclusivity indexes and tenant/courier-scoped listing (INC-03: SEC-02, COR-02, COR-08, COR-09).
 * Tests run outside a test transaction so concurrent calls commit independently; seeded rows are
 * removed per test by their random tenant ids.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DispatchServicePostgresTest {

    private static final boolean DOCKER_AVAILABLE = DockerClientFactory.instance().isDockerAvailable();

    @BeforeAll
    static void requiresDockerOrExternalDatabase() {
        Assumptions.assumeTrue(DOCKER_AVAILABLE || System.getenv("SPRING_DATASOURCE_URL") != null,
                "Skipping dispatch Postgres tests: no Docker environment and no SPRING_DATASOURCE_URL override");
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

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DispatchJobRepository jobRepository;
    @Autowired private DeliveryRepository deliveryRepository;
    @Autowired private DeliveryEventRepository deliveryEventRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CourierRepository courierRepository;

    private final List<UUID> tenants = new ArrayList<>();
    private UUID tenantId;
    private UUID otherTenantId;
    private UUID zoneId;
    private Courier courierA;
    private Courier courierB;
    private CourierService courierService;
    private TenantContext tenantContext;
    private DispatchService service;
    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        tenantId = newTenant();
        otherTenantId = newTenant();
        zoneId = UUID.randomUUID();
        courierA = newCourier(tenantId);
        courierB = newCourier(tenantId);

        courierService = mock(CourierService.class);
        tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenAnswer(inv -> java.util.Optional.of(tenantId));
        actAsCourier(courierA);
        service = newService(Duration.ofSeconds(60));
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        for (UUID t : tenants) {
            jdbc.update("DELETE FROM dispatch_jobs WHERE tenant_id = ?", t);
            jdbc.update("DELETE FROM deliveries WHERE tenant_id = ?", t);
            jdbc.update("DELETE FROM orders WHERE tenant_id = ?", t);
            jdbc.update("DELETE FROM couriers WHERE tenant_id = ?", t);
            jdbc.update("DELETE FROM app_user_profiles WHERE tenant_id = ?", t);
            jdbc.update("DELETE FROM tenants WHERE id = ?", t);
        }
    }

    // ---- acceptance -----------------------------------------------------------------------------

    @Test
    void twoConcurrentAcceptsOfTheSameJobYieldExactlyOneSuccess() throws Exception {
        UUID[] seeded = readyOrderWithDelivery(tenantId);
        UUID jobId = insertJob(tenantId, seeded[0], seeded[1], courierA.getId(), "ASSIGNED", Instant.now().plusSeconds(60));
        jdbc.update("UPDATE deliveries SET courier_id = ?, status = 'ASSIGNED' WHERE id = ?", courierA.getId(), seeded[1]);

        List<Object> outcomes = race(2, () -> service.accept(jobId));

        long successes = outcomes.stream().filter(DispatchJobResponse.class::isInstance).count();
        List<BusinessException> failures = outcomes.stream()
                .filter(BusinessException.class::isInstance).map(BusinessException.class::cast).toList();
        assertThat(successes).isEqualTo(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT status FROM dispatch_jobs WHERE id = ?", String.class, jobId)).isEqualTo("ACCEPTED");
        assertThat(jdbc.queryForObject("SELECT version FROM dispatch_jobs WHERE id = ?", Long.class, jobId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT status FROM deliveries WHERE id = ?", String.class, seeded[1])).isEqualTo("ACCEPTED");
    }

    @Test
    void acceptByAnotherCourierIsNotFoundAndLeavesJobUntouched() {
        UUID[] seeded = readyOrderWithDelivery(tenantId);
        UUID jobId = insertJob(tenantId, seeded[0], seeded[1], courierB.getId(), "ASSIGNED", Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> inTx(() -> service.accept(jobId))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> inTx(() -> service.reject(jobId, "sabotage"))).isInstanceOf(NotFoundException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM dispatch_jobs WHERE id = ?", String.class, jobId)).isEqualTo("ASSIGNED");
    }

    @Test
    void acceptAfterOfferExpiredIsConflict() {
        UUID[] seeded = readyOrderWithDelivery(tenantId);
        UUID jobId = insertJob(tenantId, seeded[0], seeded[1], courierA.getId(), "ASSIGNED", Instant.now().minusSeconds(1));

        assertThatThrownBy(() -> inTx(() -> service.accept(jobId)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(jdbc.queryForObject("SELECT status FROM dispatch_jobs WHERE id = ?", String.class, jobId)).isEqualTo("ASSIGNED");
    }

    @Test
    void acceptOfAlreadyAcceptedJobIsConflict() {
        UUID[] seeded = readyOrderWithDelivery(tenantId);
        UUID jobId = insertJob(tenantId, seeded[0], seeded[1], courierA.getId(), "ACCEPTED", null);

        assertThatThrownBy(() -> inTx(() -> service.accept(jobId)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    // ---- assignment -----------------------------------------------------------------------------

    @Test
    void assignOfOrderNotReadyForPickupIsConflict() {
        UUID[] seeded = orderWithDelivery(tenantId, "PREPARING");
        when(courierService.eligibleInZone(zoneId)).thenReturn(List.of(courierA));

        assertThatThrownBy(() -> inTx(() -> service.assign(seeded[0], seeded[1], zoneId)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(jobCount(tenantId)).isZero();
    }

    @Test
    void assignSetsOfferExpiryAndSkipsBusyCourier() {
        UUID[] busy = readyOrderWithDelivery(tenantId);
        jdbc.update("UPDATE deliveries SET courier_id = ?, status = 'PICKED_UP' WHERE id = ?", courierA.getId(), busy[1]);
        UUID[] seeded = readyOrderWithDelivery(tenantId);
        when(courierService.eligibleInZone(zoneId)).thenReturn(List.of(courierA, courierB));

        Instant before = Instant.now();
        DispatchJobResponse job = inTx(() -> service.assign(seeded[0], seeded[1], zoneId));

        assertThat(job.courierId()).isEqualTo(courierB.getId());
        Timestamp expiresAt = jdbc.queryForObject("SELECT expires_at FROM dispatch_jobs WHERE id = ?", Timestamp.class, job.id());
        assertThat(expiresAt).isNotNull();
        assertThat(expiresAt.toInstant()).isBetween(before.plusSeconds(59), Instant.now().plusSeconds(61));
    }

    @Test
    void assignWhenOnlyBusyCouriersAreEligibleIsConflict() {
        UUID[] busy = readyOrderWithDelivery(tenantId);
        jdbc.update("UPDATE deliveries SET courier_id = ?, status = 'ASSIGNED' WHERE id = ?", courierA.getId(), busy[1]);
        UUID[] seeded = readyOrderWithDelivery(tenantId);
        when(courierService.eligibleInZone(zoneId)).thenReturn(List.of(courierA));

        assertThatThrownBy(() -> inTx(() -> service.assign(seeded[0], seeded[1], zoneId)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_jobs WHERE delivery_id = ?", Long.class, seeded[1])).isZero();
    }

    @Test
    void secondAssignForADeliveryWithAnActiveJobIsConflict() {
        UUID[] seeded = readyOrderWithDelivery(tenantId);
        when(courierService.eligibleInZone(zoneId)).thenReturn(List.of(courierA, courierB));
        inTx(() -> service.assign(seeded[0], seeded[1], zoneId));
        // Force the order back to READY_FOR_PICKUP so only the active-job guard can stop the second assign.
        jdbc.update("UPDATE orders SET status = 'READY_FOR_PICKUP' WHERE id = ?", seeded[0]);

        assertThatThrownBy(() -> inTx(() -> service.assign(seeded[0], seeded[1], zoneId)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_jobs WHERE delivery_id = ?", Long.class, seeded[1])).isEqualTo(1L);
    }

    @Test
    void concurrentAssignsCannotGiveTheSameCourierTwoActiveDeliveries() throws Exception {
        UUID[] first = readyOrderWithDelivery(tenantId);
        UUID[] second = readyOrderWithDelivery(tenantId);
        when(courierService.eligibleInZone(zoneId)).thenReturn(List.of(courierA));
        List<UUID[]> targets = List.of(first, second);
        java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger();

        List<Object> outcomes = race(2, () -> {
            UUID[] target = targets.get(next.getAndIncrement());
            return service.assign(target[0], target[1], zoneId);
        });

        assertThat(outcomes.stream().filter(DispatchJobResponse.class::isInstance).count()).isEqualTo(1);
        assertThat(outcomes).filteredOn(BusinessException.class::isInstance)
                .singleElement()
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM deliveries WHERE courier_id = ? AND status = 'ASSIGNED'", Long.class, courierA.getId()))
                .isEqualTo(1L);
    }

    @Test
    void reassignRefreshesTheOfferExpiry() {
        UUID[] seeded = readyOrderWithDelivery(tenantId);
        UUID jobId = insertJob(tenantId, seeded[0], seeded[1], courierA.getId(), "REASSIGNABLE", Instant.now().minusSeconds(600));
        jdbc.update("UPDATE deliveries SET courier_id = ?, status = 'REASSIGNED' WHERE id = ?", courierA.getId(), seeded[1]);
        when(courierService.eligibleInZone(zoneId)).thenReturn(List.of(courierA, courierB));

        DispatchJobResponse job = inTx(() -> service.reassign(jobId, zoneId));

        assertThat(job.courierId()).isEqualTo(courierB.getId());
        Timestamp expiresAt = jdbc.queryForObject("SELECT expires_at FROM dispatch_jobs WHERE id = ?", Timestamp.class, jobId);
        assertThat(expiresAt.toInstant()).isAfter(Instant.now());
    }

    // ---- exclusivity indexes (V035) -------------------------------------------------------------

    @Test
    void databaseRejectsASecondActiveJobForTheSameDelivery() {
        UUID[] seeded = readyOrderWithDelivery(tenantId);
        insertJob(tenantId, seeded[0], seeded[1], courierA.getId(), "ASSIGNED", null);
        // A terminal job for the same delivery is allowed (partial index predicate).
        insertJob(tenantId, seeded[0], seeded[1], courierA.getId(), "CANCELLED", null);

        assertThatThrownBy(() -> insertJob(tenantId, seeded[0], seeded[1], courierB.getId(), "REASSIGNABLE", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_dispatch_jobs_active_delivery");
    }

    @Test
    void databaseRejectsASecondActiveDeliveryForTheSameCourier() {
        UUID[] first = readyOrderWithDelivery(tenantId);
        UUID[] done = readyOrderWithDelivery(tenantId);
        UUID[] second = readyOrderWithDelivery(tenantId);
        jdbc.update("UPDATE deliveries SET courier_id = ?, status = 'ON_ROUTE_TO_CUSTOMER' WHERE id = ?", courierA.getId(), first[1]);
        // Finished deliveries do not count towards exclusivity.
        jdbc.update("UPDATE deliveries SET courier_id = ?, status = 'DELIVERED' WHERE id = ?", courierA.getId(), done[1]);

        assertThatThrownBy(() -> jdbc.update("UPDATE deliveries SET courier_id = ?, status = 'ASSIGNED' WHERE id = ?", courierA.getId(), second[1]))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_deliveries_active_courier");
    }

    // ---- listing --------------------------------------------------------------------------------

    @Test
    void listIsTenantScopedAndCouriersOnlySeeTheirOwnJobs() {
        UUID[] a = readyOrderWithDelivery(tenantId);
        UUID[] b = readyOrderWithDelivery(tenantId);
        UUID jobA = insertJob(tenantId, a[0], a[1], courierA.getId(), "ASSIGNED", null);
        UUID jobB = insertJob(tenantId, b[0], b[1], courierB.getId(), "ASSIGNED", null);
        Courier foreign = newCourier(otherTenantId);
        UUID[] f = readyOrderWithDelivery(otherTenantId);
        UUID jobForeign = insertJob(otherTenantId, f[0], f[1], foreign.getId(), "ASSIGNED", null);

        actAsCourier(courierA);
        assertThat(inTx(() -> service.list(0, 50))).extracting(DispatchJobResponse::id).containsExactly(jobA);

        actAsCourier(courierB);
        assertThat(inTx(() -> service.list(0, 50))).extracting(DispatchJobResponse::id).containsExactly(jobB);

        actAs("ROLE_OPS");
        assertThat(inTx(() -> service.list(0, 50))).extracting(DispatchJobResponse::id)
                .containsExactlyInAnyOrder(jobA, jobB)
                .doesNotContain(jobForeign);
        assertThat(inTx(() -> service.list(0, 1))).hasSize(1);
    }

    @Test
    void listPageSizeIsCapped() {
        actAs("ROLE_OPS");
        for (int i = 0; i < 3; i++) {
            UUID[] s = readyOrderWithDelivery(tenantId);
            insertJob(tenantId, s[0], s[1], null, "ASSIGNED", null);
        }
        assertThat(inTx(() -> service.list(0, 10_000))).hasSize(3);
        assertThat(DispatchService.MAX_PAGE_SIZE).isLessThanOrEqualTo(200);
    }

    // ---- helpers --------------------------------------------------------------------------------

    private DispatchService newService(Duration offerTtl) {
        return new DispatchService(jobRepository, courierService, deliveryRepository, deliveryEventRepository,
                orderRepository, new DispatchMapper(), mock(AuditLogService.class), tenantContext, offerTtl);
    }

    private void actAsCourier(Courier courier) {
        when(courierService.requireMine()).thenReturn(courier);
        actAs("ROLE_COURIER");
    }

    private void actAs(String authority) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", null, authority));
    }

    private <T> T inTx(Supplier<T> action) {
        return tx.execute(status -> action.get());
    }

    /** Runs the action concurrently in N threads (each in its own transaction) released by one latch. */
    private List<Object> race(int threads, Supplier<Object> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Object> task = () -> {
                ready.countDown();
                go.await();
                try {
                    return tx.execute(status -> action.get());
                } catch (BusinessException e) {
                    return e;
                }
            };
            futures.add(pool.submit(task));
        }
        ready.await(5, TimeUnit.SECONDS);
        go.countDown();
        List<Object> outcomes = new ArrayList<>();
        for (Future<Object> future : futures) {
            outcomes.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return outcomes;
    }

    private long jobCount(UUID tenant) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_jobs WHERE tenant_id = ?", Long.class, tenant);
    }

    private UUID newTenant() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', 'MZN', now(), now())",
                id, "Tenant " + id, "slug-" + id);
        tenants.add(id);
        return id;
    }

    private Courier newCourier(UUID tenant) {
        UUID profileId = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'Courier', 'ACTIVE', now(), now())",
                profileId, tenant, "kc-" + profileId, profileId + "@example.test");
        Courier courier = new Courier(UUID.randomUUID(), tenant, profileId, zoneId);
        courier.approve();
        courier.setAvailable(true);
        return courierRepository.saveAndFlush(courier);
    }

    private UUID[] readyOrderWithDelivery(UUID tenant) {
        return orderWithDelivery(tenant, "READY_FOR_PICKUP");
    }

    /** Inserts an order in the given status plus its DISPATCH_PENDING delivery; returns {orderId, deliveryId}. */
    private UUID[] orderWithDelivery(UUID tenant, String orderStatus) {
        UUID orderId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, status, subtotal, fees, taxes, discounts,
                                    total, checkout_idempotency_key, delivery_confirmation_code_hash,
                                    delivery_confirmation_code_display, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 10, 0, 0, 0, 10, ?, ?, '000000', now(), now())""",
                orderId, tenant, "ref-" + orderId, UUID.randomUUID(), UUID.randomUUID(), orderStatus, "key-" + orderId, "hash-" + orderId);
        UUID deliveryId = UUID.randomUUID();
        jdbc.update("INSERT INTO deliveries (id, tenant_id, order_id, status, confirmation_code_hash, confirmation_attempts, created_at, updated_at) VALUES (?, ?, ?, 'DISPATCH_PENDING', ?, 0, now(), now())",
                deliveryId, tenant, orderId, "hash-" + deliveryId);
        return new UUID[] {orderId, deliveryId};
    }

    private UUID insertJob(UUID tenant, UUID orderId, UUID deliveryId, UUID courierId, String status, Instant expiresAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO dispatch_jobs (id, tenant_id, order_id, delivery_id, courier_id, status, assigned_at, expires_at, created_at, updated_at, version) VALUES (?, ?, ?, ?, ?, ?, now(), ?, now(), now(), 0)",
                id, tenant, orderId, deliveryId, courierId, status, expiresAt == null ? null : Timestamp.from(expiresAt));
        return id;
    }
}
