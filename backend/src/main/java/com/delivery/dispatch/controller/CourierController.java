package com.delivery.dispatch.controller;

import com.delivery.dispatch.dto.CourierResponse;
import com.delivery.dispatch.dto.CourierEarningsSummaryResponse;
import com.delivery.dispatch.dto.SetCourierAvailabilityRequest;
import com.delivery.dispatch.service.CourierService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
