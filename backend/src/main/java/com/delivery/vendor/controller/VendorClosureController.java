package com.delivery.vendor.controller;

import com.delivery.vendor.dto.CreateVendorClosureRequest;
import com.delivery.vendor.dto.VendorClosureResponse;
import com.delivery.vendor.service.VendorClosureService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Exceptional store closures (LAC-27): OPS/ADMIN for any vendor, VENDOR_ADMIN for its own vendor. */
@RestController
@RequestMapping("/api/v1/vendors/{vendorId}/closures")
public class VendorClosureController {
    private final VendorClosureService closureService;

    public VendorClosureController(VendorClosureService closureService) {
        this.closureService = closureService;
    }

    /** Capped array (LAC-14): first 200 current/future closures, earliest first, {@code X-Truncated: true} when more exist. */
    @GetMapping
    @PreAuthorize("hasAnyRole('OPS','ADMIN','VENDOR_ADMIN')")
    public ResponseEntity<List<VendorClosureResponse>> list(@PathVariable UUID vendorId) {
        return closureService.list(vendorId).toResponse();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OPS','ADMIN','VENDOR_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public VendorClosureResponse create(@PathVariable UUID vendorId, @Valid @RequestBody CreateVendorClosureRequest request) {
        return closureService.create(vendorId, request);
    }

    @DeleteMapping("/{closureId}")
    @PreAuthorize("hasAnyRole('OPS','ADMIN','VENDOR_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID vendorId, @PathVariable UUID closureId) {
        closureService.delete(vendorId, closureId);
    }
}
