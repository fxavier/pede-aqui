package com.delivery.dispatch.controller;

import com.delivery.dispatch.dto.CourierLocationResponse;
import com.delivery.dispatch.service.CourierLocationService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Operations view of courier positions (LAC-40): latest positions of the on-duty couriers of a zone. */
@RestController
@RequestMapping("/api/v1/ops/couriers")
@PreAuthorize("hasAnyRole('OPS','ADMIN')")
public class OperationsCourierLocationController {
    private final CourierLocationService locationService;

    public OperationsCourierLocationController(CourierLocationService locationService) {
        this.locationService = locationService;
    }

    /** Latest positions in the zone (couriers' operating zone), most recently seen first; newest 200, X-Truncated. */
    @GetMapping("/locations")
    public ResponseEntity<List<CourierLocationResponse>> latestInZone(@RequestParam UUID zoneId) {
        return locationService.latestInZone(zoneId).toResponse();
    }
}
