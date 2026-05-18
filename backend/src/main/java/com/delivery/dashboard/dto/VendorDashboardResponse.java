package com.delivery.dashboard.dto;

import java.math.BigDecimal;

/** Summary metrics shown on vendor dashboards. */
public record VendorDashboardResponse(long ordersByStatusCount, long rejectedOrdersCount, BigDecimal salesTotal) {}
