package com.delivery.report.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * KPI totals for a sales reporting window (contract: SalesSummary). W6-A (additive): {@code vatIncluded} = VAT contained
 * in {@code gross} (extracted at checkout; pre-W6-A orders contribute their added tax line) and {@code grossExVat} =
 * gross − vatIncluded. Refunds are not split into VAT (a validar com contabilista).
 */
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
        long cancelledCount,
        BigDecimal vatIncluded,
        BigDecimal grossExVat) {

    /** Pre-W6-A shape (VAT figures zero), kept for callers and tests. */
    public SalesSummaryResponse(Instant from, Instant to, long orderCount, BigDecimal gross, BigDecimal discountTotal, BigDecimal refunds,
                                BigDecimal net, BigDecimal commission, BigDecimal averageOrderValue, long deliveredCount, long cancelledCount) {
        this(from, to, orderCount, gross, discountTotal, refunds, net, commission, averageOrderValue, deliveredCount, cancelledCount,
                BigDecimal.ZERO.setScale(2), gross);
    }
}
