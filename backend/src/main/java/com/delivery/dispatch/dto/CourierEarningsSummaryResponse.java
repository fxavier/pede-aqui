package com.delivery.dispatch.dto;

import java.math.BigDecimal;

/** Courier earnings summary derived from delivery outcomes in the tenant. */
public record CourierEarningsSummaryResponse(int completedDeliveries, int failedDeliveries, BigDecimal earningsTotalMzn) {}
