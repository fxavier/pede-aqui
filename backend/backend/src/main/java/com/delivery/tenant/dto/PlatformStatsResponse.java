package com.delivery.tenant.dto;

/** Cross-tenant aggregate statistics for the platform super-admin dashboard. */
public record PlatformStatsResponse(
        long totalTenants,
        long activeTenants,
        long inactiveTenants,
        long totalUsers,
        long totalCouriers,
        long totalVendors) {}
