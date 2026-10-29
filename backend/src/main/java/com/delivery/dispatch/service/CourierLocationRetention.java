package com.delivery.dispatch.service;

import com.delivery.dispatch.config.CourierLocationProperties;
import com.delivery.dispatch.repository.CourierLocationRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Route-history retention (LAC-40 / LAC-63): deletes {@code courier_location_history} rows received more than
 * {@code app.courier-location.history-retention} (30 days) ago, in batches of {@code retention-batch-size} rows (each
 * its own short statement, so locks and WAL stay small), at most {@code retention-max-batches} per run; what is left
 * is picked up by the next run. Run daily by {@code CourierLocationRetentionJob}.
 */
@Service
public class CourierLocationRetention {
    private final CourierLocationRepository locationRepository;
    private final CourierLocationProperties properties;
    private Clock clock = Clock.systemUTC();

    public CourierLocationRetention(CourierLocationRepository locationRepository, CourierLocationProperties properties) {
        this.locationRepository = locationRepository;
        this.properties = properties;
    }

    /** Test seam: fixes "now". */
    public void setClock(Clock clock) { this.clock = clock; }

    /** Deletes expired history rows; returns how many were deleted. */
    public long purge() {
        Instant cutoff = clock.instant().minus(properties.getHistoryRetention());
        long total = 0;
        for (int batch = 0; batch < properties.getRetentionMaxBatches(); batch++) {
            int deleted = locationRepository.deleteHistoryReceivedBefore(cutoff, properties.getRetentionBatchSize());
            total += deleted;
            if (deleted < properties.getRetentionBatchSize()) {
                break;
            }
        }
        return total;
    }
}
