package com.delivery.dispatch.controller;

import com.delivery.dispatch.dto.DispatchJobResponse;
import com.delivery.dispatch.dto.RejectDispatchRequest;
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

/** Exposes dispatch assignment and courier job actions. */
@RestController
@RequestMapping("/api/v1/dispatch-jobs")
public class DispatchController {
    private final DispatchService dispatchService;

    public DispatchController(DispatchService dispatchService) { this.dispatchService = dispatchService; }

    @GetMapping
    @PreAuthorize("hasAnyRole('COURIER','OPS','ADMIN')")
    public List<DispatchJobResponse> list() { return dispatchService.list(); }

    @PostMapping("/assign")
    @PreAuthorize("hasAnyRole('OPS','ADMIN')")
    public DispatchJobResponse assign(@RequestParam UUID orderId, @RequestParam UUID deliveryId, @RequestParam UUID operatingZoneId) {
        return dispatchService.assign(orderId, deliveryId, operatingZoneId);
    }

    @PostMapping("/{jobId}/accept")
    @PreAuthorize("hasRole('COURIER')")
    public DispatchJobResponse accept(@PathVariable UUID jobId) { return dispatchService.accept(jobId); }

    @PostMapping("/{jobId}/reject")
    @PreAuthorize("hasRole('COURIER')")
    public DispatchJobResponse reject(@PathVariable UUID jobId, @Valid @RequestBody RejectDispatchRequest request) {
        return dispatchService.reject(jobId, request.reason());
    }

    @PostMapping("/{jobId}/reassign")
    @PreAuthorize("hasAnyRole('OPS','ADMIN')")
    public DispatchJobResponse reassign(@PathVariable UUID jobId, @RequestParam UUID operatingZoneId) { return dispatchService.reassign(jobId, operatingZoneId); }
}
