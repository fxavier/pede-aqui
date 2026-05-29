package com.delivery.dispatch.controller;

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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','COURIER')")
    @ResponseStatus(HttpStatus.CREATED)
    public CourierResponse create(@Valid @RequestBody CreateCourierRequest request) {
        return courierService.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public List<CourierResponse> listAll() {
        return courierService.listAll();
    }

    @GetMapping("/{courierId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public CourierResponse getById(@PathVariable UUID courierId) {
        return courierService.getById(courierId);
    }

    @PostMapping("/{courierId}/documents")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    @ResponseStatus(HttpStatus.CREATED)
    public CourierDocumentResponse addDocument(@PathVariable UUID courierId, @Valid @RequestBody CreateCourierDocumentRequest request) {
        return courierService.addDocument(courierId, request);
    }

    @GetMapping("/{courierId}/documents")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public List<CourierDocumentResponse> listDocuments(@PathVariable UUID courierId) {
        return courierService.listDocuments(courierId);
    }
}
