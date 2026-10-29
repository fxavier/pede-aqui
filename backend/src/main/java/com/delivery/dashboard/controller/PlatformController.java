package com.delivery.dashboard.controller;

import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.dashboard.dto.PlatformStatsResponse;
import com.delivery.dispatch.repository.CourierRepository;
import com.delivery.tenant.api.TenantStats;
import com.delivery.vendor.repository.VendorRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes platform-wide endpoints available only to the super-admin (no tenant context). Lives in the dashboard
 * module (cross-domain aggregate reader) since ADR-0002 W3-A: in the tenant module its courier count made
 * tenant depend on dispatch, closing the cycle tenant -> dispatch -> order -> cart -> tenant.
 */
@RestController
@RequestMapping("/api/v1/platform")
@PreAuthorize("@tenantContext.isPlatformAdmin()")
public class PlatformController {
    private final TenantStats tenantStats;
    private final AppUserProfileRepository userRepository;
    private final CourierRepository courierRepository;
    private final VendorRepository vendorRepository;

    public PlatformController(
            TenantStats tenantStats,
            AppUserProfileRepository userRepository,
            CourierRepository courierRepository,
            VendorRepository vendorRepository) {
        this.tenantStats = tenantStats;
        this.userRepository = userRepository;
        this.courierRepository = courierRepository;
        this.vendorRepository = vendorRepository;
    }

    /** Returns cross-tenant aggregate stats for the platform dashboard. */
    @GetMapping("/stats")
    public PlatformStatsResponse stats() {
        long total = tenantStats.countAll();
        long active = tenantStats.countByStatus("ACTIVE");
        return new PlatformStatsResponse(
                total,
                active,
                total - active,
                userRepository.count(),
                courierRepository.count(),
                vendorRepository.count());
    }
}
