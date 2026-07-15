package com.delivery.report.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** KPI totals for a sales reporting window (contract: SalesSummary). */
public record SalesSummaryResponse(
        Instant from,
        Instant to,
        long orderCount,
        BigDecimal gross,
        BigDecimal discountTotal,
        BigDecimal refunds,
        BigDecimal net,
        BigDecimal commission,
        BigDecimal averageOrderValue,
        long deliveredCount,
        long cancelledCount) {
}
