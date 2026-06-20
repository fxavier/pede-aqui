package com.delivery.tenant.controller;

import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.dispatch.repository.CourierRepository;
import com.delivery.tenant.dto.PlatformStatsResponse;
import com.delivery.tenant.repository.TenantRepository;
import com.delivery.vendor.repository.VendorRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes platform-wide endpoints available only to the super-admin (no tenant context). */
@RestController
@RequestMapping("/api/v1/platform")
@PreAuthorize("@tenantContext.isPlatformAdmin()")
public class PlatformController {
    private final TenantRepository tenantRepository;
    private final AppUserProfileRepository userRepository;
    private final CourierRepository courierRepository;
    private final VendorRepository vendorRepository;

    public PlatformController(
            TenantRepository tenantRepository,
            AppUserProfileRepository userRepository,
            CourierRepository courierRepository,
            VendorRepository vendorRepository) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.courierRepository = courierRepository;
        this.vendorRepository = vendorRepository;
    }

    /** Returns cross-tenant aggregate stats for the platform dashboard. */
    @GetMapping("/stats")
    public PlatformStatsResponse stats() {
        long total = tenantRepository.count();
        long active = tenantRepository.countByStatus("ACTIVE");
        return new PlatformStatsResponse(
                total,
                active,
                total - active,
                userRepository.count(),
                courierRepository.count(),
                vendorRepository.count());
    }
}
