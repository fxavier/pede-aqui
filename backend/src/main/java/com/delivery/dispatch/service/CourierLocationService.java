package com.delivery.dispatch.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.observability.BusinessMetrics;
import com.delivery.common.pagination.CappedList;
import com.delivery.common.pagination.PageLimits;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditActions;
import com.delivery.common.service.AuditLogService;
import com.delivery.delivery.entity.Delivery;
import com.delivery.delivery.repository.DeliveryRepository;
import com.delivery.dispatch.config.CourierLocationProperties;
import com.delivery.dispatch.dto.CourierLocationBatchRequest;
import com.delivery.dispatch.dto.CourierLocationBatchResponse;
import com.delivery.dispatch.dto.CourierLocationPointRequest;
import com.delivery.dispatch.dto.CourierLocationResponse;
import com.delivery.dispatch.dto.CourierResponse;
import com.delivery.dispatch.dto.LocationConsentResponse;
import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.entity.CourierStatus;
import com.delivery.dispatch.mapper.DispatchMapper;
import com.delivery.dispatch.repository.CourierLocationRepository;
import com.delivery.dispatch.repository.CourierLocationRepository.StoredLocation;
import com.delivery.dispatch.repository.CourierLocationRepository.ZoneLocation;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Courier GPS (LAC-40): consent, ingestion of location batches and the OPS view of latest positions.
 *
 * <p>Rules, in order: the caller must be a courier with consent to the current text version (409
 * {@code location_consent_required}) and on duty — status AVAILABLE/BUSY or working an accepted delivery (409
 * {@code courier_off_duty}). Points are then filtered (accuracy, age, clock skew, implausible speed, duplicates);
 * the newest accepted point updates {@code courier_last_locations} (+ Redis GEO/presence after commit) and, only while
 * the courier works a delivery, every accepted point is appended to {@code courier_location_history}. Coordinates are
 * never logged.
 */
@Service
public class CourierLocationService {
    static final String METRIC = "courier.locations.points";
    static final String LOW_ACCURACY = "low_accuracy";
    static final String TOO_OLD = "too_old";
    static final String FUTURE_TIMESTAMP = "future_timestamp";
    static final String IMPLAUSIBLE_SPEED = "implausible_speed";
    static final String DUPLICATE = "duplicate";

    private final CourierService courierService;
    private final CourierStatusService statusService;
    private final DeliveryRepository deliveryRepository;
    private final CourierLocationRepository locationRepository;
    private final CourierPresenceStore presenceStore;
    private final CourierLocationProperties properties;
    private final AuditLogService auditLogService;
    private final TenantContext tenantContext;
    private final DispatchMapper mapper;
    private Clock clock = Clock.systemUTC();
    private BusinessMetrics metrics = BusinessMetrics.NOOP;

    public CourierLocationService(CourierService courierService, CourierStatusService statusService, DeliveryRepository deliveryRepository,
                                  CourierLocationRepository locationRepository, CourierPresenceStore presenceStore,
                                  CourierLocationProperties properties, AuditLogService auditLogService, TenantContext tenantContext,
                                  DispatchMapper mapper) {
        this.courierService = courierService;
        this.statusService = statusService;
        this.deliveryRepository = deliveryRepository;
        this.locationRepository = locationRepository;
        this.presenceStore = presenceStore;
        this.properties = properties;
        this.auditLogService = auditLogService;
        this.tenantContext = tenantContext;
        this.mapper = mapper;
    }

    @Autowired(required = false)
    public void setBusinessMetrics(BusinessMetrics metrics) { this.metrics = metrics; }

    /** Test seam: fixes "now". */
    void setClock(Clock clock) { this.clock = clock; }

    /** The caller's consent state and the version the app must present. */
    @Transactional(readOnly = true)
    public LocationConsentResponse myConsent() {
        Courier courier = courierService.requireMine();
        return consentResponse(courier);
    }

    /**
     * Records the authenticated courier's explicit GPS consent (timestamp + text version). The version must be the
     * current one (409 {@code location_consent_outdated}); repeating it refreshes the timestamp. Audited.
     */
    @Transactional
    public LocationConsentResponse acceptConsent(String version) {
        if (!properties.getConsentVersion().equals(version.strip())) {
            throw new BusinessException("location_consent_outdated",
                    "The accepted consent text is not the current version; show the current text and retry", HttpStatus.CONFLICT);
        }
        Courier courier = courierService.requireMine();
        courier.acceptLocationConsent(properties.getConsentVersion(), clock.instant());
        auditLogService.log(AuditActions.COURIER_LOCATION_CONSENT_ACCEPTED, "courier", courier.getId().toString(),
                properties.getConsentVersion(), "SUCCESS");
        return consentResponse(courier);
    }

    /**
     * Withdraws the consent: refused while the courier works an accepted delivery (409
     * {@code courier_has_active_delivery}); otherwise the courier goes OFFLINE (if on duty) and the latest position is
     * deleted. History already recorded stays until its 30-day retention. Audited.
     */
    @Transactional
    public CourierResponse withdrawConsent() {
        Courier courier = courierService.requireMine();
        if (statusService.hasEngagedDelivery(courier)) {
            throw new BusinessException("courier_has_active_delivery",
                    "Finish or hand over the active delivery before withdrawing the location consent", HttpStatus.CONFLICT);
        }
        if (courier.getStatus().isOnDuty()) {
            statusService.goOffDuty(courier, CourierStatus.OFFLINE);
        } else {
            statusService.clearPresence(courier);
        }
        courier.withdrawLocationConsent();
        auditLogService.log(AuditActions.COURIER_LOCATION_CONSENT_WITHDRAWN, "courier", courier.getId().toString(), null, "SUCCESS");
        return mapper.toCourierResponse(courier);
    }

    /** Ingests a batch of GPS fixes from the authenticated courier (rules in the class comment). */
    @Transactional
    public CourierLocationBatchResponse ingest(CourierLocationBatchRequest request) {
        Courier courier = courierService.requireMine();
        if (!courier.hasLocationConsent(properties.getConsentVersion())) {
            reject("consent_required");
            throw new BusinessException("location_consent_required",
                    "The courier has not accepted the current location consent", HttpStatus.CONFLICT);
        }
        Optional<Delivery> engaged = deliveryRepository.findFirstByTenantIdAndCourierIdAndStatusIn(
                courier.getTenantId(), courier.getId(), CourierStatusService.ENGAGED_DELIVERY_STATUSES);
        if (!courier.getStatus().isOnDuty() && engaged.isEmpty()) {
            reject("off_duty");
            throw new BusinessException("courier_off_duty",
                    "Locations are only accepted while the courier is on duty", HttpStatus.CONFLICT);
        }
        if (request.points().size() > properties.getMaxBatchSize()) {
            throw new BusinessException("location_batch_too_large",
                    "At most " + properties.getMaxBatchSize() + " points per batch", HttpStatus.BAD_REQUEST);
        }

        Instant now = clock.instant();
        Optional<StoredLocation> last = locationRepository.findLast(courier.getId());
        Filtered filtered = filter(courier.getId(), request.points(), last.orElse(null), now);

        if (filtered.accepted().isEmpty()) {
            locationRepository.touch(courier.getId(), now);
        } else {
            StoredLocation newest = filtered.accepted().getLast();
            locationRepository.upsertLast(courier.getTenantId(), newest);
            engaged.ifPresent(delivery -> locationRepository.insertHistory(courier.getTenantId(), delivery.getId(), filtered.accepted()));
            if (last.isEmpty() || newest.recordedAt().isAfter(last.get().recordedAt())) {
                presenceStore.recordPosition(courier.getTenantId(), courier.getId(), newest.latitude(), newest.longitude(), newest.recordedAt());
            }
        }
        for (int i = 0; i < filtered.accepted().size(); i++) {
            metrics.increment(METRIC, "result", "accepted", "reason", BusinessMetrics.NO_REASON);
        }
        filtered.dropped().forEach((reason, count) -> {
            for (int i = 0; i < count; i++) {
                metrics.increment(METRIC, "result", "dropped", "reason", reason);
            }
        });
        int dropped = filtered.dropped().values().stream().mapToInt(Integer::intValue).sum();
        return new CourierLocationBatchResponse(filtered.accepted().size(), dropped, filtered.dropped());
    }

    /**
     * OPS/ADMIN: latest positions of the zone's on-duty couriers, most recently seen first, capped at 200
     * ({@code X-Truncated}). Off-duty couriers never appear (their position is deleted when they go off duty).
     */
    @Transactional(readOnly = true)
    public CappedList<CourierLocationResponse> latestInZone(UUID zoneId) {
        UUID tenantId = tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
        int cap = PageLimits.LEGACY_LIST_CAP;
        List<ZoneLocation> rows = locationRepository.findOnDutyInZone(tenantId, zoneId, cap + 1);
        Instant staleBefore = clock.instant().minus(properties.getPresenceTtl());
        List<CourierLocationResponse> items = rows.stream().limit(cap).map(row -> {
            StoredLocation l = row.location();
            return new CourierLocationResponse(row.courierId(), CourierStatus.valueOf(row.status()), l.latitude(), l.longitude(),
                    l.accuracyMeters(), l.speedMps(), l.headingDegrees(), l.recordedAt(), l.receivedAt(), l.receivedAt().isBefore(staleBefore));
        }).toList();
        return new CappedList<>(items, rows.size() > cap);
    }

    private record Filtered(List<StoredLocation> accepted, Map<String, Integer> dropped) {}

    /** Applies the quality filters in recordedAt order; the speed check chains from the last stored / accepted point. */
    private Filtered filter(UUID courierId, List<CourierLocationPointRequest> points, StoredLocation stored, Instant now) {
        List<CourierLocationPointRequest> ordered = new ArrayList<>(points);
        ordered.sort(Comparator.comparing(CourierLocationPointRequest::recordedAt));
        Instant oldestAllowed = now.minus(properties.getMaxPointAge());
        Instant newestAllowed = now.plus(properties.getMaxClockSkew());
        List<StoredLocation> accepted = new ArrayList<>();
        Map<String, Integer> dropped = new TreeMap<>();
        StoredLocation previous = stored;
        for (CourierLocationPointRequest p : ordered) {
            String reason = null;
            if (p.accuracyMeters() > properties.getMaxAccuracyMeters()) {
                reason = LOW_ACCURACY;
            } else if (p.recordedAt().isBefore(oldestAllowed)) {
                reason = TOO_OLD;
            } else if (p.recordedAt().isAfter(newestAllowed)) {
                reason = FUTURE_TIMESTAMP;
            } else if (previous != null && !p.recordedAt().isAfter(previous.recordedAt())) {
                // Same or older than the reference point: a resend (same instant) or an out-of-order straggler.
                reason = p.recordedAt().equals(previous.recordedAt()) ? DUPLICATE : null;
            }
            if (reason == null && previous != null && p.recordedAt().isAfter(previous.recordedAt())
                    && impliedSpeedKmh(previous, p) > properties.getMaxSpeedKmh()) {
                reason = IMPLAUSIBLE_SPEED;
            }
            if (reason != null) {
                dropped.merge(reason, 1, Integer::sum);
                continue;
            }
            StoredLocation point = new StoredLocation(courierId, p.latitude(), p.longitude(), p.accuracyMeters(), p.speedMps(),
                    p.headingDegrees(), p.recordedAt(), now);
            accepted.add(point);
            if (previous == null || point.recordedAt().isAfter(previous.recordedAt())) {
                previous = point;
            }
        }
        accepted.sort(Comparator.comparing(StoredLocation::recordedAt));
        return new Filtered(accepted, dropped);
    }

    /** Great-circle (haversine) distance between the two fixes divided by the elapsed time, in km/h. */
    static double impliedSpeedKmh(StoredLocation from, CourierLocationPointRequest to) {
        double seconds = Duration.between(from.recordedAt(), to.recordedAt()).toMillis() / 1000.0;
        if (seconds <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return haversineKm(from.latitude(), from.longitude(), to.latitude(), to.longitude()) / (seconds / 3600.0);
    }

    static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371.0088 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private LocationConsentResponse consentResponse(Courier courier) {
        return new LocationConsentResponse(properties.getConsentVersion(), courier.getLocationConsentVersion(),
                courier.getLocationConsentAcceptedAt(), courier.hasLocationConsent(properties.getConsentVersion()));
    }

    /** Counts a whole batch refused before filtering (one increment per batch, not per point). */
    private void reject(String reason) {
        metrics.increment(METRIC, "result", "batch_rejected", "reason", reason);
    }
}
