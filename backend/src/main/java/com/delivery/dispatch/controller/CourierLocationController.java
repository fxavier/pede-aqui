package com.delivery.dispatch.controller;

import com.delivery.dispatch.dto.CourierLocationBatchRequest;
import com.delivery.dispatch.dto.CourierLocationBatchResponse;
import com.delivery.dispatch.dto.CourierResponse;
import com.delivery.dispatch.dto.LocationConsentRequest;
import com.delivery.dispatch.dto.LocationConsentResponse;
import com.delivery.dispatch.service.CourierLocationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Courier-side GPS endpoints (LAC-40): location consent and location batches. COURIER only. */
@RestController
@RequestMapping("/api/v1/couriers/me")
@PreAuthorize("hasRole('COURIER')")
public class CourierLocationController {
    private final CourierLocationService locationService;

    public CourierLocationController(CourierLocationService locationService) {
        this.locationService = locationService;
    }

    /** Current consent text version and what the courier accepted. */
    @GetMapping("/location-consent")
    public LocationConsentResponse consent() {
        return locationService.myConsent();
    }

    /** Records the courier's explicit consent to the current text version (onboarding). */
    @PutMapping("/location-consent")
    public LocationConsentResponse acceptConsent(@Valid @RequestBody LocationConsentRequest request) {
        return locationService.acceptConsent(request.version());
    }

    /** Withdraws the consent; the courier goes offline and their latest position is deleted. */
    @DeleteMapping("/location-consent")
    public CourierResponse withdrawConsent() {
        return locationService.withdrawConsent();
    }

    /** Batch of 1..50 GPS fixes; rate-limited by the {@code courier-location} group. */
    @PostMapping("/locations")
    public CourierLocationBatchResponse ingest(@Valid @RequestBody CourierLocationBatchRequest request) {
        return locationService.ingest(request);
    }
}
