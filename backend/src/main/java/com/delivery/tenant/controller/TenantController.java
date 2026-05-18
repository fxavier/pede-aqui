package com.delivery.tenant.controller;

import com.delivery.tenant.dto.CreateTenantRequest;
import com.delivery.tenant.dto.TenantResponse;
import com.delivery.tenant.dto.UpdateTenantStatusRequest;
import com.delivery.tenant.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Exposes administrator tenant management endpoints. */
@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {
    private final TenantService service;

    public TenantController(TenantService service) {
        this.service = service;
    }

    @Operation(summary = "Create a tenant")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse create(@Valid @RequestBody CreateTenantRequest request) {
        return service.createTenant(request);
    }

    @Operation(summary = "List tenants")
    @GetMapping
    public List<TenantResponse> list() {
        return service.listTenants();
    }

    @Operation(summary = "Get tenant details")
    @GetMapping("/{id}")
    public TenantResponse get(@PathVariable UUID id) {
        return service.getTenant(id);
    }

    @Operation(summary = "Update tenant status")
    @PatchMapping("/{id}/status")
    public TenantResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateTenantStatusRequest request) {
        return service.updateStatus(id, request.status());
    }
}
