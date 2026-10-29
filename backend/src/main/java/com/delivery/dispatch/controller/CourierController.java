package com.delivery.dispatch.controller;

import com.delivery.common.pagination.PageLimits;
import com.delivery.common.pagination.PageResponse;
import com.delivery.dispatch.dto.CourierResponse;
import com.delivery.dispatch.dto.CourierEarningsSummaryResponse;
import com.delivery.dispatch.dto.CreateCourierRequest;
import com.delivery.dispatch.dto.CreateCourierDocumentRequest;
import com.delivery.dispatch.dto.CourierDocumentResponse;
import com.delivery.dispatch.dto.SetCourierAvailabilityRequest;
import com.delivery.dispatch.service.CourierService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Exposes courier profile and availability endpoints. */
@RestController
@RequestMapping("/api/v1/couriers")
public class CourierController {
    private final CourierService courierService;

    public CourierController(CourierService courierService) { this.courierService = courierService; }

    @GetMapping("/me")
    @PreAuthorize("hasRole('COURIER')")
    public CourierResponse me() { return courierService.ensureMine(); }

    @PatchMapping("/me/availability")
    @PreAuthorize("hasRole('COURIER')")
    public CourierResponse setAvailability(@RequestBody SetCourierAvailabilityRequest request) {
        return courierService.setMyAvailability(request.available());
    }

    @GetMapping("/me/earnings-summary")
    @PreAuthorize("hasRole('COURIER')")
    public CourierEarningsSummaryResponse myEarningsSummary() {
        return courierService.myEarningsSummary();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPS','COURIER')")
    @ResponseStatus(HttpStatus.CREATED)
    public CourierResponse create(@Valid @RequestBody CreateCourierRequest request) {
        return courierService.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPS')")
    public ResponseEntity<List<CourierResponse>> listAll() {
        return courierService.listAll().toResponse(); // LAC-14: newest 200, X-Truncated when capped
    }

    /** Offset page of couriers (LAC-14): size 1..100 (default 20), newest first. */
    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('ADMIN','OPS')")
    public PageResponse<CourierResponse> listPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageLimits.DEFAULT_PAGE_SIZE) int size) {
        return courierService.listPage(page, size);
    }

    @GetMapping("/{courierId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPS')")
    public CourierResponse getById(@PathVariable UUID courierId) {
        return courierService.getById(courierId);
    }

    @PostMapping("/{courierId}/documents")
    @PreAuthorize("hasAnyRole('ADMIN','OPS')")
    @ResponseStatus(HttpStatus.CREATED)
    public CourierDocumentResponse addDocument(@PathVariable UUID courierId, @Valid @RequestBody CreateCourierDocumentRequest request) {
        return courierService.addDocument(courierId, request);
    }

    @GetMapping("/{courierId}/documents")
    @PreAuthorize("hasAnyRole('ADMIN','OPS')")
    public List<CourierDocumentResponse> listDocuments(@PathVariable UUID courierId) {
        return courierService.listDocuments(courierId);
    }

    @PatchMapping("/{courierId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','OPS')")
    public CourierResponse approve(@PathVariable UUID courierId) {
        return courierService.approveCourier(courierId);
    }

    @PatchMapping("/{courierId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','OPS')")
    public CourierResponse reject(@PathVariable UUID courierId) {
        return courierService.rejectCourier(courierId);
    }
}
