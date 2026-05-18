package com.delivery.dashboard.dto;

/** Summary metrics shown on courier dashboards. */
public record CourierDashboardResponse(long completedDeliveries, long failedDeliveries, long activeAssignments) {}
