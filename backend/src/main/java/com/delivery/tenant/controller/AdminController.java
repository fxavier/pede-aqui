package com.delivery.tenant.controller;

import com.delivery.auth.dto.CreateUserProfileRequest;
import com.delivery.auth.dto.MeResponse;
import com.delivery.auth.service.AppUserProfileService;
import com.delivery.common.dto.AuditLogResponse;
import com.delivery.common.pagination.CursorPage;
import com.delivery.common.pagination.PageLimits;
import com.delivery.common.pagination.PageResponse;
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
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    private final AppUserProfileService appUserProfileService;

    public AdminController(
            TenantService tenantService,
            ZoneService zoneService,
            PolicyService policyService,
            AuditLogService auditLogService,
            AppUserProfileService appUserProfileService) {
        this.tenantService = tenantService;
        this.zoneService = zoneService;
        this.policyService = policyService;
        this.auditLogService = auditLogService;
        this.appUserProfileService = appUserProfileService;
    }

    /**
     * Platform super-admin: capped legacy array (LAC-14) of every tenant, newest first, {@code X-Truncated: true} when
     * more exist. A tenant-scoped ADMIN gets only its own tenant (W3-D: it used to list every tenant).
     */
    @GetMapping("/tenants")
    public ResponseEntity<List<TenantResponse>> tenants() { return tenantService.listTenantsForCurrentAdmin().toResponse(); }

    @PostMapping("/zones")
    public ZoneResponse createZone(@Valid @RequestBody CreateZoneRequest request) { return zoneService.create(request); }

    @GetMapping("/zones")
    public List<ZoneResponse> zones() { return zoneService.list(); }

    @PutMapping("/policy")
    public FeePolicyResponse upsertPolicy(@Valid @RequestBody UpsertFeePolicyRequest request) { return policyService.upsert(request); }

    @GetMapping("/policy")
    public FeePolicyResponse policy() { return policyService.get(); }

    /** Capped legacy array (LAC-14): newest 200 audit entries, {@code X-Truncated: true} when more exist. */
    @GetMapping("/audit")
    public ResponseEntity<List<AuditLogResponse>> audit() { return auditLogService.list().toResponse(); }

    /** Keyset page of audit entries (LAC-14): {@code { items, nextCursor }}, limit 1..200 (default 50). */
    @GetMapping("/audit/cursor")
    public CursorPage<AuditLogResponse> auditPage(
            @RequestParam(defaultValue = "" + PageLimits.DEFAULT_CURSOR_LIMIT) int limit,
            @RequestParam(required = false) String cursor) {
        return auditLogService.listPage(limit, cursor);
    }

    @PostMapping("/users")
    // LAC-13: assigning roles is ADMIN-only (method-level rules override the class rule, so state it here).
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public MeResponse createUser(@Valid @RequestBody CreateUserProfileRequest request) {
        return appUserProfileService.createUserProfile(request);
    }

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('ADMIN','SUPPORT')")
    public ResponseEntity<List<MeResponse>> listUsers() {
        return appUserProfileService.listUserProfiles().toResponse();
    }

    /** Offset page of tenant user profiles (LAC-14): size 1..100 (default 20), newest first. */
    @GetMapping("/users/page")
    @PreAuthorize("hasAnyRole('ADMIN','SUPPORT')")
    public PageResponse<MeResponse> listUsersPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageLimits.DEFAULT_PAGE_SIZE) int size) {
        return appUserProfileService.listUserProfilesPage(page, size);
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPPORT')")
    public MeResponse getUser(@PathVariable UUID id) {
        return appUserProfileService.getUserProfileById(id);
    }

}
