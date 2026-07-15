package com.delivery.report.dto;

import java.math.BigDecimal;

/** One per-product sales row including quantity sold from snapshots (contract: ProductDimensionRow). */
public record ProductDimensionRowResponse(
        String key,
        String label,
        BigDecimal gross,
        BigDecimal refunds,
        BigDecimal net,
        BigDecimal commission,
        double sharePercent,
        long quantitySold) {
}
