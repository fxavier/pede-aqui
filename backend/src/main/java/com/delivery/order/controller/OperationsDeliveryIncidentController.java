package com.delivery.order.controller;

import com.delivery.common.pagination.PageLimits;
import com.delivery.common.pagination.PageResponse;
import com.delivery.order.dto.DeliveryIncidentResponse;
import com.delivery.order.dto.ResolveDeliveryIncidentRequest;
import com.delivery.order.entity.DeliveryIncidentStatus;
import com.delivery.order.service.DeliveryIncidentService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Delivery incidents queue (LAC-44, W7-B), OPS/ADMIN only (the evidence holds the courier's position, which LAC-40
 * keeps to OPS/ADMIN): list, detail and resolution (refund, cost bearer, goods disposition, optional override with
 * reason) — tenant-scoped, audited.
 */
@RestController
@RequestMapping("/api/v1/ops/delivery-incidents")
@PreAuthorize("hasAnyRole('OPS','ADMIN')")
public class OperationsDeliveryIncidentController {
    private final DeliveryIncidentService service;

    public OperationsDeliveryIncidentController(DeliveryIncidentService service) { this.service = service; }

    /** Offset page {@code { content, page, size, totalElements }} of incidents in {@code status} (default OPEN), newest first. */
    @GetMapping
    public PageResponse<DeliveryIncidentResponse> listDeliveryIncidents(
            @RequestParam(required = false) DeliveryIncidentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageLimits.DEFAULT_PAGE_SIZE) int size) {
        return service.list(status, page, size);
    }

    /** One incident with its evidence, default decision and resolution; another tenant's → 404. */
    @GetMapping("/{incidentId}")
    public DeliveryIncidentResponse getDeliveryIncident(@PathVariable UUID incidentId) {
        return service.get(incidentId);
    }

    /**
     * Resolves the incident: omitted fields keep the policy default; any difference needs {@code overrideReason}.
     * Same decision again → 200 unchanged; another decision on a resolved incident → 409 incident_already_resolved.
     */
    @PostMapping("/{incidentId}/resolve")
    public DeliveryIncidentResponse resolveDeliveryIncident(@PathVariable UUID incidentId,
                                            @Valid @RequestBody(required = false) ResolveDeliveryIncidentRequest request) {
        return service.resolve(incidentId, request);
    }
}
