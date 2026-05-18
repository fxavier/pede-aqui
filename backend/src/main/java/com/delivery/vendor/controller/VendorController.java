package com.delivery.vendor.controller;

import com.delivery.vendor.dto.CreateVendorRequest;
import com.delivery.vendor.dto.CreateVendorDocumentRequest;
import com.delivery.vendor.dto.UpdateVendorAvailabilityRequest;
import com.delivery.vendor.dto.UpdateVendorOpeningHoursRequest;
import com.delivery.vendor.dto.UpdateVendorProfileRequest;
import com.delivery.vendor.dto.VendorDocumentResponse;
import com.delivery.vendor.dto.VendorOpeningHourResponse;
import com.delivery.vendor.dto.VendorResponse;
import com.delivery.vendor.dto.VendorVerificationDecisionRequest;
import com.delivery.vendor.service.VendorService;
import com.delivery.vendor.service.VendorVerificationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Exposes vendor onboarding, profile, and verification endpoints. */
@RestController
@RequestMapping("/api/v1/vendors")
public class VendorController {
    private final VendorService vendorService;
    private final VendorVerificationService verificationService;

    public VendorController(VendorService vendorService, VendorVerificationService verificationService) {
        this.vendorService = vendorService;
        this.verificationService = verificationService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN','ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public VendorResponse create(@Valid @RequestBody CreateVendorRequest request) {
        return vendorService.create(request);
    }

    @GetMapping
    public List<VendorResponse> list(@RequestParam(required = false) Boolean available) {
        return vendorService.list(available);
    }

    @PatchMapping("/{vendorId}/availability")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN','VENDOR_STAFF','ADMIN')")
    public VendorResponse updateAvailability(@PathVariable UUID vendorId, @Valid @RequestBody UpdateVendorAvailabilityRequest request) {
        return vendorService.updateAvailability(vendorId, request);
    }

    @PatchMapping("/{vendorId}/profile")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN','ADMIN')")
    public VendorResponse updateProfile(@PathVariable UUID vendorId, @Valid @RequestBody UpdateVendorProfileRequest request) {
        return vendorService.updateProfile(vendorId, request);
    }

    @PostMapping("/{vendorId}/documents")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN','VENDOR_STAFF','ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public VendorDocumentResponse addDocument(@PathVariable UUID vendorId, @Valid @RequestBody CreateVendorDocumentRequest request) {
        return vendorService.addDocument(vendorId, request);
    }

    @GetMapping("/{vendorId}/documents")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN','VENDOR_STAFF','ADMIN')")
    public List<VendorDocumentResponse> listDocuments(@PathVariable UUID vendorId) {
        return vendorService.listDocuments(vendorId);
    }

    @PutMapping("/{vendorId}/opening-hours")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN','VENDOR_STAFF','ADMIN')")
    public List<VendorOpeningHourResponse> replaceOpeningHours(@PathVariable UUID vendorId, @Valid @RequestBody UpdateVendorOpeningHoursRequest request) {
        return vendorService.replaceOpeningHours(vendorId, request);
    }

    @GetMapping("/{vendorId}/opening-hours")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN','VENDOR_STAFF','ADMIN')")
    public List<VendorOpeningHourResponse> listOpeningHours(@PathVariable UUID vendorId) {
        return vendorService.listOpeningHours(vendorId);
    }

    @PatchMapping("/{vendorId}/verification")
    @PreAuthorize("hasRole('ADMIN')")
    public VendorResponse decideVerification(@PathVariable UUID vendorId, @Valid @RequestBody VendorVerificationDecisionRequest request) {
        return verificationService.decide(vendorId, request.approved(), request.reason());
    }
}
