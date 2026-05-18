package com.delivery.vendor.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.vendor.dto.VendorResponse;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.mapper.VendorMapper;
import com.delivery.vendor.repository.VendorRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles admin verification decisions for vendor onboarding. */
@Service
public class VendorVerificationService {
    private final VendorRepository vendorRepository;
    private final VendorMapper vendorMapper;
    private final TenantContext tenantContext;

    public VendorVerificationService(VendorRepository vendorRepository, VendorMapper vendorMapper, TenantContext tenantContext) {
        this.vendorRepository = vendorRepository;
        this.vendorMapper = vendorMapper;
        this.tenantContext = tenantContext;
    }

    /** Approves or rejects a vendor verification request for the current tenant. */
    @Transactional
    public VendorResponse decide(UUID vendorId, boolean approved, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("decision_reason_required", "A verification reason is required", HttpStatus.BAD_REQUEST);
        }
        Vendor vendor = vendorRepository.findByTenantIdAndId(tenantId(), vendorId).orElseThrow(() -> new NotFoundException("Vendor was not found"));
        if (approved) {
            vendor.approveVerification();
        } else {
            vendor.rejectVerification();
            vendor.setAvailability(false);
        }
        return vendorMapper.toResponse(vendor);
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
