package com.delivery.dispatch.service;

import com.delivery.common.observability.BusinessMetrics;
import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.entity.CourierStatus;
import com.delivery.dispatch.repository.CourierLocationRepository;
import com.delivery.dispatch.repository.CourierLocationRepository.StoredLocation;
import com.delivery.dispatch.repository.CourierRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Presence sweeper (LAC-42), run by {@code CourierPresenceSweeperJob} every minute on one replica. Two passes, each a
 * bounded read-only candidate query followed by one transaction per courier that claims the row with
 * {@code FOR UPDATE SKIP LOCKED}, re-checks under the lock and applies the change (work-queue pattern, CLAUDE.md):
 * <ol>
 *   <li>self-heal: BUSY couriers with no accepted delivery left (a missed release) → AVAILABLE;</li>
 *   <li>timeout (only with {@code app.courier-presence.enforce-timeout=true}): AVAILABLE couriers that went online
 *       more than {@code offline-after} ago and sent no location since → OFFLINE (presence and latest position dropped).
 *       Off by default until the courier app sends locations (LAC-40 sender), otherwise every online courier would be
 *       switched off two minutes after going online.</li>
 * </ol>
 * Metric {@code courier_presence_sweeper_couriers_total{outcome=healed|offline|skipped_busy}}.
 */
@Service
public class CourierPresenceSweeper {
    static final String METRIC = "courier.presence.sweeper.couriers";

    /** Counts of one sweep, for the job log line. */
    public record Result(int healed, int offline, int skipped) {
        @Override
        public String toString() {
            return "healed=" + healed + " offline=" + offline + " skipped_busy=" + skipped;
        }
    }

    private final CourierRepository courierRepository;
    private final CourierLocationRepository locationRepository;
    private final CourierStatusService statusService;
    private final TransactionTemplate tx;
    private final Duration offlineAfter;
    private final boolean enforceTimeout;
    private final int batchSize;
    private Clock clock = Clock.systemUTC();
    private BusinessMetrics metrics = BusinessMetrics.NOOP;

    public CourierPresenceSweeper(CourierRepository courierRepository, CourierLocationRepository locationRepository,
                                  CourierStatusService statusService, PlatformTransactionManager transactionManager,
                                  @Value("${app.courier-presence.offline-after:PT2M}") Duration offlineAfter,
                                  @Value("${app.courier-presence.enforce-timeout:false}") boolean enforceTimeout,
                                  @Value("${app.courier-presence.batch-size:200}") int batchSize) {
        this.courierRepository = courierRepository;
        this.locationRepository = locationRepository;
        this.statusService = statusService;
        this.tx = new TransactionTemplate(transactionManager);
        this.offlineAfter = offlineAfter;
        this.enforceTimeout = enforceTimeout;
        this.batchSize = batchSize;
    }

    @Autowired(required = false)
    public void setBusinessMetrics(BusinessMetrics metrics) { this.metrics = metrics; }

    /** Test seam: fixes "now". */
    public void setClock(Clock clock) { this.clock = clock; }

    /** One sweep: self-heal pass, then (if enforced) the presence timeout pass. */
    public Result sweep() {
        return sweep(enforceTimeout);
    }

    /** One sweep with an explicit timeout switch (tests). */
    public Result sweep(boolean enforcePresenceTimeout) {
        int healed = 0;
        int offline = 0;
        int skipped = 0;
        for (UUID id : courierRepository.findIdleBusyIds(batchSize)) {
            Outcome outcome = tx.execute(status -> healOne(id));
            if (outcome == Outcome.CHANGED) healed++;
            if (outcome == Outcome.SKIPPED) skipped++;
        }
        if (enforcePresenceTimeout) {
            Instant cutoff = clock.instant().minus(offlineAfter);
            List<UUID> stale = courierRepository.findStaleAvailableIds(cutoff, batchSize);
            for (UUID id : stale) {
                Outcome outcome = tx.execute(status -> offlineOne(id, cutoff));
                if (outcome == Outcome.CHANGED) offline++;
                if (outcome == Outcome.SKIPPED) skipped++;
            }
        }
        count("healed", healed);
        count("offline", offline);
        count("skipped_busy", skipped);
        return new Result(healed, offline, skipped);
    }

    private enum Outcome { CHANGED, UNCHANGED, SKIPPED }

    private Outcome healOne(UUID id) {
        if (courierRepository.lockIdInStatusSkipLocked(id, CourierStatus.BUSY.name()).isEmpty()) {
            return Outcome.SKIPPED;
        }
        // System job without a request tenant: the row's own tenant scopes the delivery check.
        Courier courier = courierRepository.findById(id).orElseThrow();
        return statusService.releaseIfIdle(courier) ? Outcome.CHANGED : Outcome.UNCHANGED;
    }

    private Outcome offlineOne(UUID id, Instant cutoff) {
        if (courierRepository.lockIdInStatusSkipLocked(id, CourierStatus.AVAILABLE.name()).isEmpty()) {
            return Outcome.SKIPPED;
        }
        Courier courier = courierRepository.findById(id).orElseThrow();
        Optional<Instant> lastSeen = locationRepository.findLast(id).map(StoredLocation::receivedAt);
        boolean wentOnlineRecently = courier.getStatusChangedAt() != null && !courier.getStatusChangedAt().isBefore(cutoff);
        boolean seenRecently = lastSeen.isPresent() && !lastSeen.get().isBefore(cutoff);
        if (wentOnlineRecently || seenRecently || statusService.hasEngagedDelivery(courier)) {
            return Outcome.UNCHANGED;
        }
        statusService.goOffDuty(courier, CourierStatus.OFFLINE);
        return Outcome.CHANGED;
    }

    private void count(String outcome, int n) {
        for (int i = 0; i < n; i++) {
            metrics.increment(METRIC, "outcome", outcome);
        }
    }
}
