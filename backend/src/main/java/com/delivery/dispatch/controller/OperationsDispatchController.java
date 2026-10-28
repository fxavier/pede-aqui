package com.delivery.dispatch.controller;

import com.delivery.dispatch.dto.DeliveryEventResponse;
import com.delivery.dispatch.dto.DispatchJobResponse;
import com.delivery.dispatch.dto.ReassignDispatchRequest;
import com.delivery.dispatch.service.DispatchService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exposes operations-only order monitoring and dispatch reassignment actions. */
@RestController
@RequestMapping("/api/v1/ops/dispatch")
@PreAuthorize("hasAnyRole('OPS','ADMIN')")
public class OperationsDispatchController {
    private final DispatchService dispatchService;

    public OperationsDispatchController(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @GetMapping("/jobs")
    public List<DispatchJobResponse> jobs(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return dispatchService.list(page, size);
    }

    @GetMapping("/deliveries/{deliveryId}/events")
    public List<DeliveryEventResponse> deliveryEvents(@PathVariable UUID deliveryId) {
        return dispatchService.deliveryEvents(deliveryId);
    }

    @PostMapping("/jobs/{jobId}/reassign")
    public DispatchJobResponse reassign(@PathVariable UUID jobId, @Valid @RequestBody ReassignDispatchRequest request) {
        return dispatchService.reassign(jobId, request.operatingZoneId());
    }
}
