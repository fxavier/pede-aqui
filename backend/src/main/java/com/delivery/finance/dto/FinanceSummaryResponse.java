package com.delivery.finance.dto;

import java.math.BigDecimal;

/** Aggregate totals for finance dashboard sections. */
public record FinanceSummaryResponse(BigDecimal confirmedPaymentsTotal, BigDecimal commissionTotal, BigDecimal refundsTotal, BigDecimal unreconciledCashTotal) {}
