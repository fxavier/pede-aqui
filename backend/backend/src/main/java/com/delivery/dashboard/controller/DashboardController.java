package com.delivery.dashboard.controller;

import com.delivery.dashboard.dto.AdminDashboardResponse;
import com.delivery.dashboard.dto.CourierDashboardResponse;
import com.delivery.dashboard.dto.FinanceDashboardResponse;
import com.delivery.dashboard.dto.VendorDashboardResponse;
import com.delivery.dashboard.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes role-oriented marketplace dashboard endpoints. */
@RestController
@RequestMapping("/api/v1/dashboards")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) { this.dashboardService = dashboardService; }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public AdminDashboardResponse admin() { return dashboardService.admin(); }

    @GetMapping("/vendor")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN','VENDOR_STAFF')")
    public VendorDashboardResponse vendor() { return dashboardService.vendor(); }

    @GetMapping("/courier")
    @PreAuthorize("hasRole('COURIER')")
    public CourierDashboardResponse courier() { return dashboardService.courier(); }

    @GetMapping("/finance")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    public FinanceDashboardResponse finance() { return dashboardService.finance(); }
}
