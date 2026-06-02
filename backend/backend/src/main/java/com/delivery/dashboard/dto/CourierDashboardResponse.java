package com.delivery.dashboard.dto;

import java.math.BigDecimal;

/** Summary metrics shown on courier dashboards. */
public record CourierDashboardResponse(long completedDeliveries, long failedDeliveries, long activeAssignments, BigDecimal earningsTotal) {}
