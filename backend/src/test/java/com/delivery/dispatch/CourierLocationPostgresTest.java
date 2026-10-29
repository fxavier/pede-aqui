package com.delivery.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.pagination.CappedList;
import com.delivery.common.service.AuditActions;
import com.delivery.dispatch.dto.CourierLocationBatchRequest;
import com.delivery.dispatch.dto.CourierLocationBatchResponse;
import com.delivery.dispatch.dto.CourierLocationPointRequest;
import com.delivery.dispatch.dto.CourierLocationResponse;
import com.delivery.dispatch.entity.CourierStatus;
import com.delivery.dispatch.service.CourierLocationRetention;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * LAC-40 on real Postgres: consent (current version only, audited, withdrawal), ingestion rules (consent and on-duty
 * gates with their 409 codes, quality filters, newest-wins upsert of the latest position, route history only while a
 * delivery is worked), the OPS zone view (on duty only, tenant-scoped, stale flag) and the 30-day retention job.
 */
class CourierLocationPostgresTest extends CourierPostgresFixture {
    private static final double LAT = -25.9692;
    private static final double LNG = 32.5732;

    // ---- consent ------------------------------------------------------------------------------------------------

    @Test
    void consentMustBeTheCurrentVersionAndIsRecordedWithTimestampAndAudit() {
        me = courier("OFFLINE");
        assertThat(inTx(() -> locationService.myConsent()).valid()).isFalse();

        assertThatThrownBy(() -> inTx(() -> locationService.acceptConsent("2020-01-v0")))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("location_consent_outdated");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        var accepted = inTx(() -> locationService.acceptConsent(properties.getConsentVersion()));
        assertThat(accepted.valid()).isTrue();
        assertThat(accepted.acceptedVersion()).isEqualTo(properties.getConsentVersion());
        assertThat(jdbc.queryForObject("SELECT location_consent_accepted_at FROM couriers WHERE id = ?", Timestamp.class, me)).isNotNull();
        verify(auditLogService).log(AuditActions.COURIER_LOCATION_CONSENT_ACCEPTED, "courier", me.toString(), properties.getConsentVersion(), "SUCCESS");

        // Bumping the text version invalidates the old consent.
        properties.setConsentVersion("2026-11-v2");
        assertThat(inTx(() -> locationService.myConsent()).valid()).isFalse();
    }

    @Test
    void withdrawingConsentTakesTheCourierOfflineDeletesTheLatestPositionButIsRefusedMidDelivery() {
        me = courier("AVAILABLE");
        consent(me, properties.getConsentVersion());
        inTx(() -> locationService.ingest(batch(point(0, 10))));
        assertThat(lastLocationRows()).isEqualTo(1);

        var response = inTx(() -> locationService.withdrawConsent());
        assertThat(response.status()).isEqualTo(CourierStatus.OFFLINE);
        assertThat(response.locationConsentVersion()).isNull();
        assertThat(lastLocationRows()).isZero();
        verify(auditLogService).log(AuditActions.COURIER_LOCATION_CONSENT_WITHDRAWN, "courier", me.toString(), null, "SUCCESS");

        UUID busy = courier("BUSY");
        me = busy;
        consent(busy, properties.getConsentVersion());
        delivery(busy, "PICKED_UP");
        assertThatThrownBy(() -> inTx(() -> locationService.withdrawConsent()))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("courier_has_active_delivery"));
    }

    // ---- ingestion gates ----------------------------------------------------------------------------------------

    @Test
    void noLocationIsAcceptedWithoutConsentOrOffDuty() {
        me = courier("AVAILABLE");
        assertThatThrownBy(() -> inTx(() -> locationService.ingest(batch(point(0, 10)))))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("location_consent_required");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        consent(me, "an-older-version");
        assertThatThrownBy(() -> inTx(() -> locationService.ingest(batch(point(0, 10)))))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("location_consent_required"));

        consent(me, properties.getConsentVersion());
        jdbc.update("UPDATE couriers SET status = 'OFFLINE' WHERE id = ?", me);
        assertThatThrownBy(() -> inTx(() -> locationService.ingest(batch(point(0, 10)))))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("courier_off_duty");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
        jdbc.update("UPDATE couriers SET status = 'SUSPENDED' WHERE id = ?", me);
        assertThatThrownBy(() -> inTx(() -> locationService.ingest(batch(point(0, 10)))))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("courier_off_duty"));
        assertThat(lastLocationRows()).isZero();
    }

    @Test
    void aSuspendedCourierStillWorkingAnAcceptedDeliveryIsOnDuty() {
        me = courier("SUSPENDED");
        consent(me, properties.getConsentVersion());
        UUID deliveryId = delivery(me, "ON_ROUTE_TO_CUSTOMER");

        CourierLocationBatchResponse response = inTx(() -> locationService.ingest(batch(point(0, 10))));

        assertThat(response.accepted()).isEqualTo(1);
        assertThat(historyRows(deliveryId)).isEqualTo(1);
    }

    // ---- ingestion rules ----------------------------------------------------------------------------------------

    @Test
    void idlePointsUpdateOnlyTheLatestPositionWhileDeliveryPointsAlsoGoToHistory() {
        me = courier("AVAILABLE");
        consent(me, properties.getConsentVersion());

        CourierLocationBatchResponse idle = inTx(() -> locationService.ingest(batch(point(-60, 10), point(-30, 12))));
        assertThat(idle.accepted()).isEqualTo(2);
        assertThat(lastLocationRows()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM courier_location_history WHERE courier_id = ?", Integer.class, me)).isZero();

        jdbc.update("UPDATE couriers SET status = 'BUSY' WHERE id = ?", me);
        UUID deliveryId = delivery(me, "PICKED_UP");
        CourierLocationBatchResponse working = inTx(() -> locationService.ingest(batch(point(-20, 10), point(-10, 10))));
        assertThat(working.accepted()).isEqualTo(2);
        assertThat(historyRows(deliveryId)).isEqualTo(2);
        // PostGIS point is derived from lat/lng.
        assertThat(jdbc.queryForObject("SELECT ST_Y(location::geometry) FROM courier_last_locations WHERE courier_id = ?", Double.class, me))
                .isCloseTo(LAT, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void qualityFiltersDropPointsAndReportThemByReason() {
        me = courier("AVAILABLE");
        consent(me, properties.getConsentVersion());
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        List<CourierLocationPointRequest> points = List.of(
                new CourierLocationPointRequest(LAT, LNG, 150.0, null, null, now.minusSeconds(50)),            // low accuracy
                new CourierLocationPointRequest(LAT, LNG, 10.0, null, null, now.minus(Duration.ofMinutes(10))), // too old
                new CourierLocationPointRequest(LAT, LNG, 10.0, null, null, now.plus(Duration.ofMinutes(10))),  // future
                new CourierLocationPointRequest(LAT, LNG, 10.0, 5.0, 90.0, now.minusSeconds(40)),              // accepted
                new CourierLocationPointRequest(LAT, LNG, 10.0, null, null, now.minusSeconds(40)),             // duplicate instant
                // ~11 km in 10 s => ~4000 km/h
                new CourierLocationPointRequest(LAT + 0.1, LNG, 10.0, null, null, now.minusSeconds(30)),       // implausible speed
                new CourierLocationPointRequest(LAT + 0.0005, LNG, 10.0, null, null, now.minusSeconds(20)));   // ~55 m in 20 s: accepted

        CourierLocationBatchResponse response = inTx(() -> locationService.ingest(new CourierLocationBatchRequest(points)));

        assertThat(response.accepted()).isEqualTo(2);
        assertThat(response.dropped()).isEqualTo(5);
        assertThat(response.droppedByReason()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "low_accuracy", 1, "too_old", 1, "future_timestamp", 1, "duplicate", 1, "implausible_speed", 1));
        assertThat(jdbc.queryForObject("SELECT latitude FROM courier_last_locations WHERE courier_id = ?", Double.class, me))
                .isEqualTo(LAT + 0.0005);
    }

    @Test
    void anOlderBatchNeverOverwritesANewerLatestPositionButStillCountsAsPresence() {
        me = courier("AVAILABLE");
        consent(me, properties.getConsentVersion());
        inTx(() -> locationService.ingest(batch(point(-10, 10))));
        Timestamp recordedBefore = jdbc.queryForObject("SELECT recorded_at FROM courier_last_locations WHERE courier_id = ?", Timestamp.class, me);
        jdbc.update("UPDATE courier_last_locations SET received_at = received_at - interval '10 minutes' WHERE courier_id = ?", me);

        inTx(() -> locationService.ingest(batch(point(-100, 10))));

        assertThat(jdbc.queryForObject("SELECT recorded_at FROM courier_last_locations WHERE courier_id = ?", Timestamp.class, me))
                .isEqualTo(recordedBefore);
        assertThat(jdbc.queryForObject("SELECT received_at > now() - interval '1 minute' FROM courier_last_locations WHERE courier_id = ?",
                Boolean.class, me)).isTrue();
    }

    // ---- OPS view -----------------------------------------------------------------------------------------------

    @Test
    void opsSeesOnlyOnDutyCouriersOfTheZoneInItsTenantWithAStaleFlag() {
        UUID fresh = courier("AVAILABLE");
        UUID busy = courier("BUSY");
        UUID offline = courier("OFFLINE");
        for (UUID c : List.of(fresh, busy, offline)) {
            insertLastLocation(c, Instant.now());
        }
        jdbc.update("UPDATE courier_last_locations SET received_at = now() - interval '10 minutes' WHERE courier_id = ?", busy);
        UUID original = tenantId;

        CappedList<CourierLocationResponse> rows = inTx(() -> locationService.latestInZone(zoneId));

        assertThat(rows.truncated()).isFalse();
        assertThat(rows.items()).extracting(CourierLocationResponse::courierId).containsExactly(fresh, busy);
        assertThat(rows.items().get(0).stale()).isFalse();
        assertThat(rows.items().get(1).stale()).isTrue();
        assertThat(rows.items().get(1).status()).isEqualTo(CourierStatus.BUSY);

        tenantId = newTenant();
        assertThat(inTx(() -> locationService.latestInZone(zoneId)).items()).isEmpty();
        tenantId = original;
    }

    // ---- retention ----------------------------------------------------------------------------------------------

    @Test
    void retentionDeletesHistoryOlderThan30DaysInBatchesAndKeepsRecentRows() {
        me = courier("BUSY");
        UUID deliveryId = delivery(me, "PICKED_UP");
        Instant now = Instant.now();
        for (int i = 0; i < 7; i++) {
            insertHistory(deliveryId, now.minus(Duration.ofDays(31)).minusSeconds(i));
        }
        insertHistory(deliveryId, now.minus(Duration.ofDays(29)));
        insertHistory(deliveryId, now.minus(Duration.ofHours(1)));
        properties.setRetentionBatchSize(3);

        long deleted = new CourierLocationRetention(locationRepository, properties).purge();

        assertThat(deleted).isGreaterThanOrEqualTo(7); // shared database: other tests' expired rows may go too
        assertThat(historyRows(deliveryId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM courier_location_history WHERE received_at < now() - interval '30 days'",
                Integer.class)).isZero();
    }

    @Test
    void retentionStopsAfterTheMaximumNumberOfBatchesPerRun() {
        me = courier("BUSY");
        UUID deliveryId = delivery(me, "PICKED_UP");
        for (int i = 0; i < 5; i++) {
            insertHistory(deliveryId, Instant.now().minus(Duration.ofDays(40)).minusSeconds(i));
        }
        properties.setRetentionBatchSize(2);
        properties.setRetentionMaxBatches(1);

        CourierLocationRetention retention = new CourierLocationRetention(locationRepository, properties);
        assertThat(retention.purge()).isEqualTo(2);
        properties.setRetentionMaxBatches(200);
        retention.purge();
        assertThat(historyRows(deliveryId)).isZero();
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    private static CourierLocationBatchRequest batch(CourierLocationPointRequest... points) {
        return new CourierLocationBatchRequest(List.of(points));
    }

    /** A fix at Maputo, {@code secondsFromNow} relative to now, with the given accuracy. */
    private static CourierLocationPointRequest point(int secondsFromNow, double accuracy) {
        return new CourierLocationPointRequest(LAT, LNG, accuracy, 4.0, 180.0, Instant.now().plusSeconds(secondsFromNow));
    }

    private int lastLocationRows() {
        return jdbc.queryForObject("SELECT count(*) FROM courier_last_locations WHERE courier_id = ?", Integer.class, me);
    }

    private int historyRows(UUID deliveryId) {
        return jdbc.queryForObject("SELECT count(*) FROM courier_location_history WHERE delivery_id = ?", Integer.class, deliveryId);
    }

    private void insertLastLocation(UUID courierId, Instant at) {
        jdbc.update("""
                INSERT INTO courier_last_locations (courier_id, tenant_id, location, latitude, longitude, accuracy_m, recorded_at, received_at)
                VALUES (?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, 10, ?, ?)""",
                courierId, tenantId, LNG, LAT, LAT, LNG, Timestamp.from(at), Timestamp.from(at));
    }

    private void insertHistory(UUID deliveryId, Instant receivedAt) {
        jdbc.update("""
                INSERT INTO courier_location_history (tenant_id, courier_id, delivery_id, latitude, longitude, accuracy_m, recorded_at, received_at)
                VALUES (?, ?, ?, ?, ?, 10, ?, ?)""",
                tenantId, me, deliveryId, LAT, LNG, Timestamp.from(receivedAt), Timestamp.from(receivedAt));
    }
}
