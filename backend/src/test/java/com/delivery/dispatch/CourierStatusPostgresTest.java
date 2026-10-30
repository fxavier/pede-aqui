package com.delivery.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.service.AuditActions;
import com.delivery.delivery.repository.DeliveryEventRepository;
import com.delivery.dispatch.dto.CourierResponse;
import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.entity.CourierStatus;
import com.delivery.dispatch.mapper.DispatchMapper;
import com.delivery.dispatch.repository.CourierDocumentRepository;
import com.delivery.dispatch.repository.DispatchJobRepository;
import com.delivery.dispatch.service.CourierPresenceSweeper;
import com.delivery.dispatch.service.CourierService;
import com.delivery.dispatch.service.DispatchJobRetirement;
import com.delivery.dispatch.service.DispatchService;
import com.delivery.order.repository.OrderRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

/**
 * LAC-42 on real Postgres: the V060 schema (status backfill rules through the sync trigger, CHECKs), courier status
 * transitions (online/offline, OPS suspend/reinstate), dispatch eligibility (AVAILABLE only), BUSY on accept and its
 * release when the delivery ends or is retired, and the presence sweeper (self-heal, timeout, SKIP LOCKED).
 */
class CourierStatusPostgresTest extends CourierPostgresFixture {

    @Autowired private DispatchJobRepository jobRepository;
    @Autowired private DeliveryEventRepository deliveryEventRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CourierDocumentRepository documentRepository;

    // ---- V060 schema --------------------------------------------------------------------------------------------

    @Test
    void legacyBooleanWritesAreMappedOntoStatusAndAvailableStaysDerived() {
        UUID profile = com.delivery.common.TestParents.customer(jdbc, tenantId);
        UUID legacy = UUID.randomUUID();
        // Pre-V060 shape (legacy SQL seeds / old app version): only `available`.
        jdbc.update("INSERT INTO couriers (id, tenant_id, user_profile_id, verification_status, available, created_at, updated_at) VALUES (?, ?, ?, 'APPROVED', TRUE, now(), now())",
                legacy, tenantId, profile);
        assertThat(statusOf(legacy)).isEqualTo("AVAILABLE");

        jdbc.update("UPDATE couriers SET available = FALSE WHERE id = ?", legacy);
        assertThat(statusOf(legacy)).isEqualTo("OFFLINE");

        jdbc.update("UPDATE couriers SET status = 'BUSY' WHERE id = ?", legacy);
        assertThat(availableFlagOf(legacy)).isTrue();

        jdbc.update("UPDATE couriers SET status = 'SUSPENDED' WHERE id = ?", legacy);
        assertThat(availableFlagOf(legacy)).isFalse();
        // A legacy "go online" on a suspended courier does not lift the suspension.
        jdbc.update("UPDATE couriers SET available = TRUE WHERE id = ?", legacy);
        assertThat(statusOf(legacy)).isEqualTo("SUSPENDED");
        assertThat(availableFlagOf(legacy)).isFalse();
    }

    @Test
    void checksRejectUnknownStatusVehicleAndCapacityAboveOne() {
        UUID id = courier("OFFLINE");
        assertThatThrownBy(() -> jdbc.update("UPDATE couriers SET status = 'ON_BREAK' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_couriers_status");
        assertThatThrownBy(() -> jdbc.update("UPDATE couriers SET vehicle_type = 'Mota' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_couriers_vehicle_type");
        assertThatThrownBy(() -> jdbc.update("UPDATE couriers SET max_active_deliveries = 2 WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_couriers_max_active_deliveries");
        assertThat(jdbc.queryForObject("SELECT max_active_deliveries FROM couriers WHERE id = ?", Integer.class, id)).isEqualTo(1);
    }

    // ---- courier self-service ----------------------------------------------------------------------------------

    @Test
    void courierGoesOnlineAndOfflineAndOfflineDropsTheLatestPosition() {
        me = courier("OFFLINE");
        CourierResponse online = inTx(() -> statusService.setMyStatus(CourierStatus.AVAILABLE));
        assertThat(online.status()).isEqualTo(CourierStatus.AVAILABLE);
        assertThat(online.available()).isTrue();
        assertThat(statusOf(me)).isEqualTo("AVAILABLE");

        lastLocation(me);
        CourierResponse offline = inTx(() -> statusService.setMyStatus(CourierStatus.OFFLINE));
        assertThat(offline.status()).isEqualTo(CourierStatus.OFFLINE);
        assertThat(availableFlagOf(me)).isFalse();
        assertThat(lastLocationCount(me)).isZero();
    }

    @Test
    void goingOnlineWhileWorkingAnAcceptedDeliveryIsBusyAndGoingOfflineIsRefused() {
        me = courier("OFFLINE");
        delivery(me, "PICKED_UP");

        assertThat(inTx(() -> statusService.setMyStatus(CourierStatus.AVAILABLE)).status()).isEqualTo(CourierStatus.BUSY);
        assertThatThrownBy(() -> inTx(() -> statusService.setMyStatus(CourierStatus.OFFLINE)))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("courier_has_active_delivery");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
        assertThat(statusOf(me)).isEqualTo("BUSY");
    }

    @Test
    void courierCannotSetSystemStatusesNorGoOnlineWhenSuspendedOrUnverified() {
        me = courier("OFFLINE");
        assertThatThrownBy(() -> inTx(() -> statusService.setMyStatus(CourierStatus.BUSY)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("invalid_courier_status"));
        assertThatThrownBy(() -> inTx(() -> statusService.setMyStatus(CourierStatus.SUSPENDED)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        jdbc.update("UPDATE couriers SET status = 'SUSPENDED' WHERE id = ?", me);
        assertThatThrownBy(() -> inTx(() -> statusService.setMyStatus(CourierStatus.AVAILABLE)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("courier_suspended"));

        jdbc.update("UPDATE couriers SET status = 'OFFLINE', verification_status = 'PENDING' WHERE id = ?", me);
        assertThatThrownBy(() -> inTx(() -> statusService.setMyStatus(CourierStatus.AVAILABLE)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("courier_not_verified"));
        assertThat(statusOf(me)).isEqualTo("OFFLINE");
    }

    // ---- OPS suspend / reinstate --------------------------------------------------------------------------------

    @Test
    void opsSuspendsAndReinstatesWithAuditAndTheReasonStaysOffTheAuditText() {
        UUID courier = courier("AVAILABLE");
        lastLocation(courier);

        CourierResponse suspended = inTx(() -> statusService.suspend(courier, "  documentos expirados "));
        assertThat(suspended.status()).isEqualTo(CourierStatus.SUSPENDED);
        assertThat(suspended.suspensionReason()).isEqualTo("documentos expirados");
        assertThat(lastLocationCount(courier)).isZero();
        verify(auditLogService).log(AuditActions.COURIER_SUSPENDED, "courier", courier.toString(), "from=AVAILABLE", "SUCCESS");

        // Idempotent: a second suspension changes nothing and is not audited again.
        inTx(() -> statusService.suspend(courier, "outra"));
        assertThat(jdbc.queryForObject("SELECT suspension_reason FROM couriers WHERE id = ?", String.class, courier)).isEqualTo("documentos expirados");

        CourierResponse reinstated = inTx(() -> statusService.reinstate(courier));
        assertThat(reinstated.status()).isEqualTo(CourierStatus.OFFLINE);
        assertThat(reinstated.suspensionReason()).isNull();
        verify(auditLogService).log(AuditActions.COURIER_REINSTATED, "courier", courier.toString(), "to=OFFLINE", "SUCCESS");

        assertThatThrownBy(() -> inTx(() -> statusService.reinstate(courier)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("courier_not_suspended"));
    }

    @Test
    void suspendIsTenantScoped() {
        UUID courier = courier("AVAILABLE");
        UUID otherTenant = newTenant();
        UUID original = tenantId;
        tenantId = otherTenant;
        assertThatThrownBy(() -> inTx(() -> statusService.suspend(courier, "x")))
                .isInstanceOf(com.delivery.common.exception.NotFoundException.class);
        tenantId = original;
        assertThat(statusOf(courier)).isEqualTo("AVAILABLE");
        verify(auditLogService, never()).log(eq(AuditActions.COURIER_SUSPENDED), anyString(), anyString(), anyString(), anyString());
    }

    // ---- dispatch eligibility, BUSY on accept, release ----------------------------------------------------------

    @Test
    void onlyAvailableVerifiedCouriersOfTheZoneAreEligible() {
        UUID available = courier("AVAILABLE");
        courier("OFFLINE");
        courier("BUSY");
        courier("SUSPENDED");
        UUID unverified = courier("AVAILABLE");
        jdbc.update("UPDATE couriers SET verification_status = 'PENDING' WHERE id = ?", unverified);

        CourierService real = new CourierService(courierRepository, documentRepository, null, deliveryRepository, new DispatchMapper(),
                tenantContext, null, null, auditLogService);
        assertThat(inTx(() -> real.eligibleInZone(zoneId))).extracting(Courier::getId).containsExactly(available);
    }

    @Test
    void acceptMakesTheCourierBusyAndASuspendedCourierCannotAccept() {
        me = courier("AVAILABLE");
        UUID deliveryId = delivery(me, "ASSIGNED");
        UUID orderId = jdbc.queryForObject("SELECT order_id FROM deliveries WHERE id = ?", UUID.class, deliveryId);
        UUID job = insertJob(orderId, deliveryId, me);
        DispatchService dispatch = dispatchService();

        jdbc.update("UPDATE couriers SET status = 'SUSPENDED' WHERE id = ?", me);
        assertThatThrownBy(() -> inTx(() -> dispatch.accept(job)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("courier_suspended"));
        assertThat(jdbc.queryForObject("SELECT status FROM dispatch_jobs WHERE id = ?", String.class, job)).isEqualTo("ASSIGNED");

        jdbc.update("UPDATE couriers SET status = 'AVAILABLE' WHERE id = ?", me);
        inTx(() -> dispatch.accept(job));
        assertThat(statusOf(me)).isEqualTo("BUSY");
        assertThat(availableFlagOf(me)).isTrue();
    }

    @Test
    void deliveryEndReleasesABusyCourierOnlyWhenNoAcceptedDeliveryRemains() {
        UUID courier = courier("BUSY");
        UUID deliveryId = delivery(courier, "ARRIVED_AT_CUSTOMER");

        inTx(() -> { statusService.onCourierFreed(tenantId, courier); return null; });
        assertThat(statusOf(courier)).isEqualTo("BUSY"); // still holds the delivery

        setDeliveryStatus(deliveryId, "DELIVERED");
        inTx(() -> { statusService.onCourierFreed(tenantId, courier); return null; });
        assertThat(statusOf(courier)).isEqualTo("AVAILABLE");

        // Never touches a suspended or offline courier.
        jdbc.update("UPDATE couriers SET status = 'SUSPENDED' WHERE id = ?", courier);
        inTx(() -> { statusService.onCourierFreed(tenantId, courier); return null; });
        assertThat(statusOf(courier)).isEqualTo("SUSPENDED");
    }

    @Test
    void retiringTheDeliveryCancelsTheJobAndFreesTheBusyCourier() {
        UUID courier = courier("BUSY");
        UUID deliveryId = delivery(courier, "ACCEPTED");
        UUID orderId = jdbc.queryForObject("SELECT order_id FROM deliveries WHERE id = ?", UUID.class, deliveryId);
        UUID job = insertJob(orderId, deliveryId, courier);
        jdbc.update("UPDATE dispatch_jobs SET status = 'ACCEPTED' WHERE id = ?", job);
        DispatchJobRetirement retirement = new DispatchJobRetirement(jobRepository);
        retirement.setCourierListener(statusService);

        inTx(() -> {
            deliveryRepository.findByTenantIdAndId(tenantId, deliveryId).orElseThrow()
                    .updateStatus(com.delivery.delivery.entity.DeliveryStatus.CANCELLED);
            retirement.onDeliveryRetired(tenantId, deliveryId);
            return null;
        });

        assertThat(jdbc.queryForObject("SELECT status FROM dispatch_jobs WHERE id = ?", String.class, job)).isEqualTo("CANCELLED");
        assertThat(statusOf(courier)).isEqualTo("AVAILABLE");
    }

    // ---- presence sweeper --------------------------------------------------------------------------------------

    @Test
    void sweeperHealsBusyCouriersWithoutAnAcceptedDelivery() {
        UUID orphan = courier("BUSY");
        UUID working = courier("BUSY");
        delivery(working, "ON_ROUTE_TO_CUSTOMER");

        CourierPresenceSweeper.Result result = sweeper().sweep(false);

        assertThat(statusOf(orphan)).isEqualTo("AVAILABLE");
        assertThat(statusOf(working)).isEqualTo("BUSY");
        assertThat(result.healed()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void sweeperTakesSilentAvailableCouriersOfflineOnlyWhenTheTimeoutIsEnforced() {
        Instant longAgo = Instant.now().minus(Duration.ofMinutes(10));
        UUID silent = courier("AVAILABLE");
        setStatusChangedAt(silent, longAgo);
        UUID fresh = courier("AVAILABLE"); // went online just now
        UUID reporting = courier("AVAILABLE");
        setStatusChangedAt(reporting, longAgo);
        lastLocation(reporting); // received_at = now
        UUID staleReport = courier("AVAILABLE");
        setStatusChangedAt(staleReport, longAgo);
        lastLocation(staleReport);
        jdbc.update("UPDATE courier_last_locations SET received_at = ? WHERE courier_id = ?", java.sql.Timestamp.from(longAgo), staleReport);

        sweeper().sweep(false);
        assertThat(statusOf(silent)).isEqualTo("AVAILABLE");

        sweeper().sweep(true);
        assertThat(statusOf(silent)).isEqualTo("OFFLINE");
        assertThat(statusOf(staleReport)).isEqualTo("OFFLINE");
        assertThat(lastLocationCount(staleReport)).isZero();
        assertThat(statusOf(fresh)).isEqualTo("AVAILABLE");
        assertThat(statusOf(reporting)).isEqualTo("AVAILABLE");
    }

    @Test
    void sweeperSkipsACourierRowHeldByAnotherTransaction() throws Exception {
        UUID held = courier("AVAILABLE");
        setStatusChangedAt(held, Instant.now().minus(Duration.ofMinutes(10)));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = pool.submit(() -> tx.execute(status -> {
                jdbc.queryForList("SELECT id FROM couriers WHERE id = ? FOR UPDATE", held);
                locked.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            CourierPresenceSweeper.Result result = sweeper().sweep(true);

            assertThat(result.skipped()).isGreaterThanOrEqualTo(1);
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
        assertThat(statusOf(held)).isEqualTo("AVAILABLE"); // retried next tick
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    private CourierPresenceSweeper sweeper() {
        return new CourierPresenceSweeper(courierRepository, locationRepository, statusService, transactionManager,
                Duration.ofMinutes(2), false, 500);
    }

    private DispatchService dispatchService() {
        return new DispatchService(jobRepository, courierService, deliveryRepository, deliveryEventRepository, orderRepository,
                new DispatchMapper(), auditLogService, tenantContext, Duration.ofSeconds(60),
                com.delivery.order.OrderTestStatus.stateMachine());
    }

    private UUID insertJob(UUID orderId, UUID deliveryId, UUID courierId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO dispatch_jobs (id, tenant_id, order_id, delivery_id, courier_id, status, assigned_at, expires_at, created_at, updated_at, version) VALUES (?, ?, ?, ?, ?, 'ASSIGNED', now(), now() + interval '1 minute', now(), now(), 0)",
                id, tenantId, orderId, deliveryId, courierId);
        return id;
    }

    private void lastLocation(UUID courierId) {
        jdbc.update("""
                INSERT INTO courier_last_locations (courier_id, tenant_id, location, latitude, longitude, accuracy_m, recorded_at, received_at)
                VALUES (?, ?, ST_SetSRID(ST_MakePoint(32.58, -25.96), 4326)::geography, -25.96, 32.58, 10, now(), now())""",
                courierId, tenantId);
    }

    private int lastLocationCount(UUID courierId) {
        return jdbc.queryForObject("SELECT count(*) FROM courier_last_locations WHERE courier_id = ?", Integer.class, courierId);
    }
}
