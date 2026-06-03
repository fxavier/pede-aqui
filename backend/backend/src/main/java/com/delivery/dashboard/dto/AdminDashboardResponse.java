package com.delivery.dashboard.dto;

import java.math.BigDecimal;
import java.util.Map;

/** Summary metrics shown on the admin dashboard. */
public record AdminDashboardResponse(
    long totalOrders,
    BigDecimal totalRevenue,
    Map<String, Long> ordersByStatus,
    long activeVendors,
    long activeCouriers,
    long cancellations,
    long failedDeliveries
) {}
