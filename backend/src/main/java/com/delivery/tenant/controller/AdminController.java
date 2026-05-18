package com.delivery.tenant.controller;

import com.delivery.common.dto.AuditLogResponse;
import com.delivery.common.service.AuditLogService;
import com.delivery.geo.dto.CreateZoneRequest;
import com.delivery.geo.dto.ZoneResponse;
import com.delivery.geo.service.ZoneService;
import com.delivery.tenant.dto.FeePolicyResponse;
import com.delivery.tenant.dto.TenantResponse;
import com.delivery.tenant.dto.UpsertFeePolicyRequest;
import com.delivery.tenant.service.PolicyService;
import com.delivery.tenant.service.TenantService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes admin endpoints for marketplace policy, verification, and audit visibility. */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final TenantService tenantService;
    private final ZoneService zoneService;
    private final PolicyService policyService;
    private final AuditLogService auditLogService;

    public AdminController(
            TenantService tenantService,
            ZoneService zoneService,
            PolicyService policyService,
            AuditLogService auditLogService) {
        this.tenantService = tenantService;
        this.zoneService = zoneService;
        this.policyService = policyService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/tenants")
    public List<TenantResponse> tenants() { return tenantService.listTenants(); }

    @PostMapping("/zones")
    public ZoneResponse createZone(@Valid @RequestBody CreateZoneRequest request) { return zoneService.create(request); }

    @GetMapping("/zones")
    public List<ZoneResponse> zones() { return zoneService.list(); }

    @PutMapping("/policy")
    public FeePolicyResponse upsertPolicy(@Valid @RequestBody UpsertFeePolicyRequest request) { return policyService.upsert(request); }

    @GetMapping("/policy")
    public FeePolicyResponse policy() { return policyService.get(); }

    @GetMapping("/audit")
    public List<AuditLogResponse> audit() { return auditLogService.list(); }

}
