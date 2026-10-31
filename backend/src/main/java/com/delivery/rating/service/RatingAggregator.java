package com.delivery.rating.service;

import com.delivery.dispatch.api.CourierRatingAggregates;
import com.delivery.rating.repository.RatingRepository;
import com.delivery.vendor.api.VendorRatingAggregates;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Keeps the vendor/courier rating aggregates (average, count) exact inside the rating transaction (LAC-28).
 * Protocol, always in this order: {@link #lock} the vendor row then the courier row BEFORE writing the rating, write
 * and flush the rating, then {@link #recompute}. The row lock serialises concurrent ratings of the same subject, and
 * the recompute is a fresh statement (READ COMMITTED) that sees every committed rating — a full recount, so it is
 * idempotent and never loses an update (an incremental "+1" would not survive a retried event; a recount does).
 */
@Component
public class RatingAggregator {
    private final RatingRepository ratingRepository;
    private final VendorRatingAggregates vendorAggregates;
    private final CourierRatingAggregates courierAggregates;

    public RatingAggregator(RatingRepository ratingRepository, VendorRatingAggregates vendorAggregates,
                            CourierRatingAggregates courierAggregates) {
        this.ratingRepository = ratingRepository;
        this.vendorAggregates = vendorAggregates;
        this.courierAggregates = courierAggregates;
    }

    /** Locks the rated vendor and (when present) courier rows, vendor first: one global order, no deadlocks. */
    public void lock(UUID tenantId, UUID vendorId, UUID courierId) {
        if (vendorId != null) {
            vendorAggregates.lock(tenantId, vendorId);
        }
        if (courierId != null) {
            courierAggregates.lock(tenantId, courierId);
        }
    }

    /** Recounts and stores the aggregates of the vendor and courier (call after the rating write was flushed). */
    public void recompute(UUID tenantId, UUID vendorId, UUID courierId) {
        if (vendorId != null) {
            Object[] stats = single(ratingRepository.vendorStats(tenantId, vendorId));
            vendorAggregates.store(tenantId, vendorId, RatingRules.average((Number) stats[0]), ((Number) stats[1]).longValue());
        }
        if (courierId != null) {
            Object[] stats = single(ratingRepository.courierStats(tenantId, courierId));
            courierAggregates.store(tenantId, courierId, RatingRules.average((Number) stats[0]), ((Number) stats[1]).longValue());
        }
    }

    private static Object[] single(List<Object[]> rows) {
        return rows.isEmpty() ? new Object[] {null, 0L} : rows.get(0);
    }
}
