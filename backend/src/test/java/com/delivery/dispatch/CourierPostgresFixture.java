package com.delivery.dispatch;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.common.PostgresTestContainerConfig;
import com.delivery.common.TestParents;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.delivery.repository.DeliveryRepository;
import com.delivery.dispatch.config.CourierLocationProperties;
import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.mapper.DispatchMapper;
import com.delivery.dispatch.repository.CourierLocationRepository;
import com.delivery.dispatch.repository.CourierRepository;
import com.delivery.dispatch.service.CourierLocationService;
import com.delivery.dispatch.service.CourierPresenceStore;
import com.delivery.dispatch.service.CourierService;
import com.delivery.dispatch.service.CourierStatusService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;

/**
 * Shared real-Postgres fixture for the W5-B courier status (LAC-42) and GPS (LAC-40) tests: services wired by hand
 * around the real repositories (the caller's courier is resolved by a mocked {@link CourierService#requireMine()} that
 * loads the row inside the running transaction), SQL seeds with real parents, cleanup per random tenant. Tests run
 * outside a test transaction; {@link #inTx} gives each service call its own committed transaction.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
abstract class CourierPostgresFixture {
    private static final boolean DOCKER_AVAILABLE = DockerClientFactory.instance().isDockerAvailable();

    @BeforeAll
    static void requiresDockerOrExternalDatabase() {
        Assumptions.assumeTrue(DOCKER_AVAILABLE || System.getenv("SPRING_DATASOURCE_URL") != null,
                "Skipping courier Postgres tests: no Docker environment and no SPRING_DATASOURCE_URL override");
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

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected PlatformTransactionManager transactionManager;
    @Autowired protected CourierRepository courierRepository;
    @Autowired protected DeliveryRepository deliveryRepository;

    private final List<UUID> tenants = new ArrayList<>();
    protected UUID tenantId;
    protected UUID zoneId;
    protected UUID me;
    protected TenantContext tenantContext;
    protected CourierService courierService;
    protected AuditLogService auditLogService;
    protected CourierLocationRepository locationRepository;
    protected CourierLocationProperties properties;
    protected CourierPresenceStore presenceStore;
    protected CourierStatusService statusService;
    protected CourierLocationService locationService;
    protected TransactionTemplate tx;

    @BeforeEach
    void setUpFixture() {
        tx = new TransactionTemplate(transactionManager);
        tenantId = newTenant();
        zoneId = TestParents.zone(jdbc, tenantId, UUID.randomUUID());
        tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenAnswer(inv -> Optional.of(tenantId));
        courierService = mock(CourierService.class);
        when(courierService.requireMine()).thenAnswer(inv -> courierRepository.findByTenantIdAndId(tenantId, me).orElseThrow());
        auditLogService = mock(AuditLogService.class);
        locationRepository = new CourierLocationRepository(jdbc);
        properties = new CourierLocationProperties();
        properties.setRedisEnabled(false);
        @SuppressWarnings("unchecked")
        ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate> noRedis = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<io.micrometer.core.instrument.MeterRegistry> noMeters = mock(ObjectProvider.class);
        presenceStore = new CourierPresenceStore(noRedis, properties, noMeters);
        DispatchMapper mapper = new DispatchMapper();
        statusService = new CourierStatusService(courierRepository, courierService, deliveryRepository, locationRepository,
                presenceStore, auditLogService, tenantContext, mapper);
        locationService = new CourierLocationService(courierService, statusService, deliveryRepository, locationRepository,
                presenceStore, properties, auditLogService, tenantContext, mapper);
    }

    @AfterEach
    void cleanUpFixture() {
        for (UUID t : tenants) {
            jdbc.update("DELETE FROM courier_location_history WHERE tenant_id = ?", t);
            jdbc.update("DELETE FROM courier_last_locations WHERE tenant_id = ?", t);
            jdbc.update("DELETE FROM dispatch_jobs WHERE tenant_id = ?", t);
            jdbc.update("DELETE FROM deliveries WHERE tenant_id = ?", t);
            jdbc.update("DELETE FROM orders WHERE tenant_id = ?", t);
            jdbc.update("DELETE FROM couriers WHERE tenant_id = ?", t);
            jdbc.update("DELETE FROM app_user_profiles WHERE tenant_id = ?", t);
            jdbc.update("DELETE FROM vendors WHERE tenant_id = ?", t);
        }
        for (UUID t : tenants) {
            jdbc.update("DELETE FROM zones WHERE tenant_id = ?", t);
            jdbc.update("DELETE FROM tenants WHERE id = ?", t);
        }
    }

    protected <T> T inTx(Supplier<T> action) {
        return tx.execute(status -> action.get());
    }

    protected UUID newTenant() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', 'MZN', now(), now())",
                id, "Tenant " + id, "slug-" + id);
        tenants.add(id);
        return id;
    }

    /** Approved courier of the fixture tenant/zone in the given status (through the entity, as the app writes it). */
    protected UUID courier(String status) {
        UUID profileId = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'Courier', 'ACTIVE', now(), now())",
                profileId, tenantId, "kc-" + profileId, profileId + "@example.test");
        Courier courier = new Courier(UUID.randomUUID(), tenantId, profileId, zoneId);
        courier.approve();
        courier.changeStatus(com.delivery.dispatch.entity.CourierStatus.valueOf(status));
        return courierRepository.saveAndFlush(courier).getId();
    }

    protected String statusOf(UUID courierId) {
        return jdbc.queryForObject("SELECT status FROM couriers WHERE id = ?", String.class, courierId);
    }

    protected boolean availableFlagOf(UUID courierId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT available FROM couriers WHERE id = ?", Boolean.class, courierId));
    }

    protected void setStatusChangedAt(UUID courierId, Instant at) {
        jdbc.update("UPDATE couriers SET status_changed_at = ? WHERE id = ?", Timestamp.from(at), courierId);
    }

    protected void consent(UUID courierId, String version) {
        jdbc.update("UPDATE couriers SET location_consent_accepted_at = now(), location_consent_version = ? WHERE id = ?", version, courierId);
    }

    /** Order + delivery held by the courier in the given delivery status; returns the delivery id. */
    protected UUID delivery(UUID courierId, String deliveryStatus) {
        UUID orderId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, status, subtotal, fees, taxes, discounts,
                                    total, checkout_idempotency_key, delivery_confirmation_code_hash,
                                    delivery_confirmation_code_display, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'ASSIGNED_TO_COURIER', 10, 0, 0, 0, 10, ?, ?, '000000', now(), now())""",
                orderId, tenantId, "ref-" + orderId, TestParents.customer(jdbc, tenantId), TestParents.vendor(jdbc, tenantId),
                "key-" + orderId, "hash-" + orderId);
        UUID deliveryId = UUID.randomUUID();
        jdbc.update("INSERT INTO deliveries (id, tenant_id, order_id, courier_id, status, confirmation_code_hash, confirmation_attempts, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 0, now(), now())",
                deliveryId, tenantId, orderId, courierId, deliveryStatus, "hash-" + deliveryId);
        return deliveryId;
    }

    protected void setDeliveryStatus(UUID deliveryId, String status) {
        jdbc.update("UPDATE deliveries SET status = ? WHERE id = ?", status, deliveryId);
    }
}
