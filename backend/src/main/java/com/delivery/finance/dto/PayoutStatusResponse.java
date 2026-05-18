package com.delivery.finance.dto;

import java.math.BigDecimal;

/** Simplified payout status summary for vendor disbursement monitoring. */
public record PayoutStatusResponse(BigDecimal pendingCommissionTotal, BigDecimal settledCommissionTotal) {}
