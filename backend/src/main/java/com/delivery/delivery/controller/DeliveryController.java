package com.delivery.delivery.controller;

import com.delivery.common.observability.BusinessMetrics;
import com.delivery.delivery.dto.CompleteDeliveryRequest;
import com.delivery.delivery.dto.DeliveryResponse;
import com.delivery.delivery.dto.UpdateDeliveryStatusRequest;
import com.delivery.delivery.service.DeliveryService;
import com.delivery.delivery.api.DeliveryAddressResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes delivery completion endpoints for couriers. */
@RestController
@RequestMapping("/api/v1/deliveries")
public class DeliveryController {
    private final DeliveryService service;

    private BusinessMetrics metrics = BusinessMetrics.NOOP;

    public DeliveryController(DeliveryService service) { this.service = service; }

    /** Optional: slice tests without a meter registry keep the no-op metrics. */
    @Autowired(required = false)
    public void setBusinessMetrics(BusinessMetrics metrics) { this.metrics = metrics; }

    @PostMapping("/{deliveryId}/complete")
    @PreAuthorize("hasRole('COURIER')")
    public DeliveryResponse complete(@PathVariable UUID deliveryId, @Valid @RequestBody CompleteDeliveryRequest request) {
        // LAC-50: deliveries_completion_total{result,reason}; a wrong PIN is reason=invalid_delivery_code and the
        // attempt that locks the code is audited (audit_events_total{action="DELIVERY_CODE_LOCKED"}).
        return metrics.recordOutcome(BusinessMetrics.DELIVERY_COMPLETION, () -> service.complete(deliveryId, request.confirmationCode()));
    }

    /** Full delivery address for the assigned courier only (403 for anyone else, incl. other couriers). */
    @GetMapping("/{deliveryId}/address")
    @PreAuthorize("hasRole('COURIER')")
    public DeliveryAddressResponse address(@PathVariable UUID deliveryId) {
        return service.getAddressForAssignedCourier(deliveryId);
    }

    @PatchMapping("/{deliveryId}/status")
    @PreAuthorize("hasAnyRole('COURIER','OPS','ADMIN')")
    public DeliveryResponse updateStatus(@PathVariable UUID deliveryId, @Valid @RequestBody UpdateDeliveryStatusRequest request) {
        // LAC-50: deliveries_status_updates_total{status,result,reason}; status=FAILED_DELIVERY counts delivery failures.
        return metrics.recordOutcome(BusinessMetrics.DELIVERY_STATUS_UPDATES,
                () -> service.updateStatus(deliveryId, request.status(), request.proofPhotoStorageKey(), request.cashCollectedAmount()),
                "status", String.valueOf(request.status()));
    }
}
