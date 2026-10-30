package com.delivery.dispatch.controller;

import com.delivery.common.pagination.PageLimits;
import com.delivery.common.pagination.PageResponse;
import com.delivery.dispatch.dto.CourierResponse;
import com.delivery.dispatch.dto.CourierEarningsSummaryResponse;
import com.delivery.dispatch.dto.CreateCourierRequest;
import com.delivery.dispatch.dto.CreateCourierDocumentRequest;
import com.delivery.dispatch.dto.CourierDocumentResponse;
import com.delivery.dispatch.dto.SetCourierAvailabilityRequest;
import com.delivery.dispatch.dto.SetCourierStatusRequest;
import com.delivery.dispatch.dto.SuspendCourierRequest;
import com.delivery.dispatch.entity.CourierStatus;
import com.delivery.dispatch.service.CourierService;
import com.delivery.dispatch.service.CourierStatusService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Exposes courier profile, operational status (LAC-42) and OPS suspension endpoints. */
@RestController
@RequestMapping("/api/v1/couriers")
public class CourierController {
    private final CourierService courierService;
    private final CourierStatusService statusService;

    public CourierController(CourierService courierService, CourierStatusService statusService) {
        this.courierService = courierService;
        this.statusService = statusService;
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('COURIER')")
    public CourierResponse me() { return courierService.ensureMine(); }

    /** Courier goes online (AVAILABLE) or offline (OFFLINE) — LAC-42. */
    @PutMapping("/me/status")
    @PreAuthorize("hasRole('COURIER')")
    public CourierResponse setStatus(@Valid @RequestBody SetCourierStatusRequest request) {
        return statusService.setMyStatus(request.status());
    }

    /** Deprecated (LAC-42): use PUT /couriers/me/status. true = AVAILABLE, false = OFFLINE, same rules. */
    @Deprecated
    @Operation(deprecated = true, summary = "Deprecated: use PUT /api/v1/couriers/me/status")
    @PatchMapping("/me/availability")
    @PreAuthorize("hasRole('COURIER')")
    public CourierResponse setAvailability(@RequestBody SetCourierAvailabilityRequest request) {
        return statusService.setMyStatus(request.available() ? CourierStatus.AVAILABLE : CourierStatus.OFFLINE);
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

    /** OPS/ADMIN: suspends the courier (out of dispatch, location no longer collected); audited — LAC-42. */
    @PatchMapping("/{courierId}/suspend")
    @PreAuthorize("hasAnyRole('ADMIN','OPS')")
    public CourierResponse suspend(@PathVariable UUID courierId, @Valid @RequestBody SuspendCourierRequest request) {
        return statusService.suspend(courierId, request.reason());
    }

    /** OPS/ADMIN: lifts a suspension; the courier is OFFLINE until they go online again; audited — LAC-42. */
    @PatchMapping("/{courierId}/reinstate")
    @PreAuthorize("hasAnyRole('ADMIN','OPS')")
    public CourierResponse reinstate(@PathVariable UUID courierId) {
        return statusService.reinstate(courierId);
    }
}
