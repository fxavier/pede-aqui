package com.delivery.vendor.api;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Write port for the vendor rating aggregate ({@code vendors.rating} = average, {@code vendors.rating_count}) owned by
 * the rating module's recomputation (LAC-28, W7-D). The {@code Vendor} entity maps both columns read-only, so a vendor
 * edit can never write back a stale average. Must run inside the caller's transaction.
 */
@Component
public class VendorRatingAggregates {
    private final JdbcTemplate jdbc;

    public VendorRatingAggregates(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Row-locks the vendor so concurrent ratings recompute one after another. {@code FOR NO KEY UPDATE} does not
     * conflict with the {@code FOR KEY SHARE} lock the ratings FK insert takes, so two raters cannot deadlock.
     * Returns false when the vendor does not exist in the tenant.
     */
    public boolean lock(UUID tenantId, UUID vendorId) {
        return !jdbc.queryForList("SELECT id FROM vendors WHERE tenant_id = ? AND id = ? FOR NO KEY UPDATE",
                UUID.class, tenantId, vendorId).isEmpty();
    }

    /** Stores the recomputed average (2 decimals, 0 without ratings) and count. */
    public void store(UUID tenantId, UUID vendorId, BigDecimal average, long count) {
        jdbc.update("UPDATE vendors SET rating = ?, rating_count = ? WHERE tenant_id = ? AND id = ?",
                average.doubleValue(), count, tenantId, vendorId);
    }
}
