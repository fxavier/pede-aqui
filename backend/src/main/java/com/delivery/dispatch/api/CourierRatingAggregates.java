package com.delivery.dispatch.api;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Write port for the courier rating aggregate ({@code couriers.rating} = average, {@code couriers.rating_count}) owned
 * by the rating module's recomputation (LAC-28, W7-D). The {@code Courier} entity maps both columns read-only.
 * Must run inside the caller's transaction.
 */
@Component
public class CourierRatingAggregates {
    private final JdbcTemplate jdbc;

    public CourierRatingAggregates(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Row-locks the courier ({@code FOR NO KEY UPDATE}, see VendorRatingAggregates); false when absent in the tenant. */
    public boolean lock(UUID tenantId, UUID courierId) {
        return !jdbc.queryForList("SELECT id FROM couriers WHERE tenant_id = ? AND id = ? FOR NO KEY UPDATE",
                UUID.class, tenantId, courierId).isEmpty();
    }

    /** Stores the recomputed average (2 decimals, 0 without ratings) and count. */
    public void store(UUID tenantId, UUID courierId, BigDecimal average, long count) {
        jdbc.update("UPDATE couriers SET rating = ?, rating_count = ? WHERE tenant_id = ? AND id = ?",
                average.doubleValue(), count, tenantId, courierId);
    }
}
