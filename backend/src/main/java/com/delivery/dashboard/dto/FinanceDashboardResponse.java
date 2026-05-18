package com.delivery.dashboard.dto;

import java.math.BigDecimal;

/** Summary metrics shown on finance dashboards. */
public record FinanceDashboardResponse(BigDecimal transactionsTotal, BigDecimal commissionsTotal, BigDecimal refundsTotal, BigDecimal unreconciledCashTotal) {}
