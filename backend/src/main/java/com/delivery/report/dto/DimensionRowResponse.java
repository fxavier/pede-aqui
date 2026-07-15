package com.delivery.report.dto;

import java.math.BigDecimal;

/** One per-dimension sales row for by-vendor / by-category reports (contract: DimensionRow). */
public record DimensionRowResponse(
        String key,
        String label,
        BigDecimal gross,
        BigDecimal refunds,
        BigDecimal net,
        BigDecimal commission,
        double sharePercent) {
}
