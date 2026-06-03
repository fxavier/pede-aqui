package com.delivery.delivery.controller;

import com.delivery.delivery.dto.CompleteDeliveryRequest;
import com.delivery.delivery.dto.DeliveryResponse;
import com.delivery.delivery.dto.UpdateDeliveryStatusRequest;
import com.delivery.delivery.service.DeliveryService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
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

    public DeliveryController(DeliveryService service) { this.service = service; }

    @PostMapping("/{deliveryId}/complete")
    @PreAuthorize("hasRole('COURIER')")
    public DeliveryResponse complete(@PathVariable UUID deliveryId, @Valid @RequestBody CompleteDeliveryRequest request) {
        return service.complete(deliveryId, request.confirmationCode());
    }

    @PatchMapping("/{deliveryId}/status")
    @PreAuthorize("hasAnyRole('COURIER','OPERATIONS','ADMIN')")
    public DeliveryResponse updateStatus(@PathVariable UUID deliveryId, @Valid @RequestBody UpdateDeliveryStatusRequest request) {
        return service.updateStatus(deliveryId, request.status(), request.proofPhotoStorageKey(), request.cashCollectedAmount());
    }
}
