package com.delivery.vendor.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.vendor.dto.CreateVendorRequest;
import com.delivery.vendor.dto.CreateVendorDocumentRequest;
import com.delivery.vendor.dto.UpdateVendorAvailabilityRequest;
import com.delivery.vendor.dto.UpdateVendorOpeningHoursRequest;
import com.delivery.vendor.dto.UpdateVendorProfileRequest;
import com.delivery.vendor.dto.VendorResponse;
import com.delivery.vendor.dto.VendorDocumentResponse;
import com.delivery.vendor.dto.VendorOpeningHourResponse;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.entity.VendorDocument;
import com.delivery.vendor.entity.VendorOpeningHour;
import com.delivery.vendor.mapper.VendorMapper;
import com.delivery.vendor.repository.VendorDocumentRepository;
import com.delivery.vendor.repository.VendorOpeningHourRepository;
import com.delivery.vendor.repository.VendorRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles vendor onboarding and profile availability updates. */
@Service
public class VendorService {
    private final VendorRepository vendorRepository;
    private final VendorDocumentRepository vendorDocumentRepository;
    private final VendorOpeningHourRepository vendorOpeningHourRepository;
    private final VendorMapper vendorMapper;
    private final TenantContext tenantContext;

    public VendorService(
            VendorRepository vendorRepository,
            VendorDocumentRepository vendorDocumentRepository,
            VendorOpeningHourRepository vendorOpeningHourRepository,
            VendorMapper vendorMapper,
            TenantContext tenantContext) {
        this.vendorRepository = vendorRepository;
        this.vendorDocumentRepository = vendorDocumentRepository;
        this.vendorOpeningHourRepository = vendorOpeningHourRepository;
        this.vendorMapper = vendorMapper;
        this.tenantContext = tenantContext;
    }

    /** Registers a vendor profile for the current tenant. */
    @Transactional
    public VendorResponse create(CreateVendorRequest request) {
        UUID tenantId = tenantId();
        Vendor vendor = new Vendor(UUID.randomUUID(), tenantId, request.name(), request.categoryId(), 
                request.latitude(), request.longitude(), request.ownerName(), request.nif(), 
                request.phone(), request.address(), request.description(), request.logoStorageKey());
        return vendorMapper.toResponse(vendorRepository.save(vendor));
    }

    /** Lists vendors. Platform admins (no tenant scope) see all; tenant users see their own. */
    @Transactional(readOnly = true)
    public List<VendorResponse> list(Boolean available) {
        if (tenantContext.isPlatformAdmin()) {
            return vendorRepository.findAll().stream().map(vendorMapper::toResponse).toList();
        }
        UUID tenantId = tenantId();
        List<Vendor> vendors = available == null
                ? vendorRepository.findByTenantId(tenantId)
                : vendorRepository.findByTenantIdAndAvailable(tenantId, available);
        return vendors.stream().map(vendorMapper::toResponse).toList();
    }

    /** Updates vendor availability and estimated delivery time. */
    @Transactional
    public VendorResponse updateAvailability(UUID vendorId, UpdateVendorAvailabilityRequest request) {
        if (request.estimatedDeliveryMinutes() <= 0) {
            throw new BusinessException("invalid_delivery_estimate", "Estimated delivery minutes must be positive", HttpStatus.BAD_REQUEST);
        }
        Vendor vendor = vendorRepository.findByTenantIdAndId(tenantId(), vendorId).orElseThrow(() -> new NotFoundException("Vendor was not found"));
        vendor.setAvailability(request.available());
        vendor.setEstimatedDeliveryMinutes(request.estimatedDeliveryMinutes());
        return vendorMapper.toResponse(vendor);
    }

    /** Updates the core vendor profile fields. */
    @Transactional
    public VendorResponse updateProfile(UUID vendorId, UpdateVendorProfileRequest request) {
        Vendor vendor = vendorRepository.findByTenantIdAndId(tenantId(), vendorId).orElseThrow(() -> new NotFoundException("Vendor was not found"));
        vendor.updateProfile(request.name(), request.categoryId(), request.latitude(), request.longitude(),
                request.ownerName(), request.nif(), request.phone(), request.address(), request.description(), request.logoStorageKey());
        return vendorMapper.toResponse(vendor);
    }

    /** Adds vendor document metadata for later verification workflows. */
    @Transactional
    public VendorDocumentResponse addDocument(UUID vendorId, CreateVendorDocumentRequest request) {
        UUID tenantId = tenantId();
        ensureVendorExists(tenantId, vendorId);
        
        // Validate document type
        if (!List.of("BUSINESS_LICENCE", "TAX_CERTIFICATE", "HEALTH_PERMIT", "OTHER").contains(request.documentType())) {
            throw new BusinessException("invalid_document_type", "Invalid vendor document type. Allowed: BUSINESS_LICENCE, TAX_CERTIFICATE, HEALTH_PERMIT, OTHER", HttpStatus.BAD_REQUEST);
        }
        
        VendorDocument document = new VendorDocument(UUID.randomUUID(), tenantId, vendorId, request.documentType(), request.storageKey());
        return vendorMapper.toDocumentResponse(vendorDocumentRepository.save(document));
    }

    /** Lists vendor document metadata for onboarding visibility. */
    @Transactional(readOnly = true)
    public List<VendorDocumentResponse> listDocuments(UUID vendorId) {
        UUID tenantId = tenantId();
        ensureVendorExists(tenantId, vendorId);
        return vendorDocumentRepository.findByTenantIdAndVendorId(tenantId, vendorId).stream().map(vendorMapper::toDocumentResponse).toList();
    }

    /** Replaces the vendor opening-hour schedule with the provided rules. */
    @Transactional
    public List<VendorOpeningHourResponse> replaceOpeningHours(UUID vendorId, UpdateVendorOpeningHoursRequest request) {
        UUID tenantId = tenantId();
        ensureVendorExists(tenantId, vendorId);
        validateOpeningHours(request);
        vendorOpeningHourRepository.deleteByTenantIdAndVendorId(tenantId, vendorId);
        List<VendorOpeningHour> toSave = request.hours().stream()
                .map(hour -> new VendorOpeningHour(UUID.randomUUID(), tenantId, vendorId, hour.dayOfWeek(), hour.opensAt(), hour.closesAt(), hour.closed()))
                .toList();
        return vendorOpeningHourRepository.saveAll(toSave).stream().map(vendorMapper::toOpeningHourResponse).toList();
    }

    /** Lists current vendor opening-hour rules ordered by weekday. */
    @Transactional(readOnly = true)
    public List<VendorOpeningHourResponse> listOpeningHours(UUID vendorId) {
        UUID tenantId = tenantId();
        ensureVendorExists(tenantId, vendorId);
        return vendorOpeningHourRepository.findByTenantIdAndVendorIdOrderByDayOfWeekAsc(tenantId, vendorId)
                .stream()
                .map(vendorMapper::toOpeningHourResponse)
                .toList();
    }

    private void ensureVendorExists(UUID tenantId, UUID vendorId) {
        vendorRepository.findByTenantIdAndId(tenantId, vendorId).orElseThrow(() -> new NotFoundException("Vendor was not found"));
    }

    private void validateOpeningHours(UpdateVendorOpeningHoursRequest request) {
        for (var hour : request.hours()) {
            if (!hour.closed() && (hour.opensAt() == null || hour.closesAt() == null)) {
                throw new BusinessException("invalid_opening_hour", "Open and close times are required when day is open", HttpStatus.BAD_REQUEST);
            }
            if (!hour.closed() && !hour.opensAt().isBefore(hour.closesAt())) {
                throw new BusinessException("invalid_opening_hour", "Opening time must be before closing time", HttpStatus.BAD_REQUEST);
            }
            if (hour.closed() && (hour.opensAt() != null || hour.closesAt() != null)) {
                throw new BusinessException("invalid_opening_hour", "Closed days must not include opening or closing times", HttpStatus.BAD_REQUEST);
            }
        }
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
