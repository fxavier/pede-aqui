package com.delivery.tenant.controller;

import com.delivery.tenant.dto.TenantProvisioningRequest;
import com.delivery.tenant.dto.TenantProvisioningResponse;
import com.delivery.tenant.service.SubscriptionProvisioningService;
import com.delivery.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** M2M endpoint for the subscriptions platform to idempotently provision tenants from leads. */
@RestController
@RequestMapping("/api/v1/provisioning")
public class SubscriptionProvisioningController {
    private final SubscriptionProvisioningService service;

    public SubscriptionProvisioningController(SubscriptionProvisioningService service) {
        this.service = service;
    }

    @Operation(summary = "Provision (or return) a tenant and owner from a subscription lead; idempotent via Idempotency-Key")
    @PostMapping("/tenants")
    public ResponseEntity<TenantProvisioningResponse> provisionTenant(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TenantProvisioningRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException("validation_failed", "Idempotency-Key header is required", HttpStatus.BAD_REQUEST);
        }
        TenantProvisioningResponse response = service.provision(idempotencyKey.trim(), request);
        return ResponseEntity.status(response.alreadyExisted() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }
}
