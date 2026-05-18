package com.delivery.dashboard.dto;

/** Summary metrics shown on the admin dashboard. */
public record AdminDashboardResponse(long activeVendors, long activeCouriers, long cancelledOrders, long failedDeliveries) {}
