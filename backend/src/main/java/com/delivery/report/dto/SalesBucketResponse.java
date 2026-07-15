package com.delivery.report.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** One time-series bucket of sales KPIs (contract: SalesBucket). */
public record SalesBucketResponse(
        Instant bucket,
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
