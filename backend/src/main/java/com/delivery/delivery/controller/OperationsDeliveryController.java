package com.delivery.delivery.controller;

import com.delivery.delivery.dto.DeliveryResponse;
import com.delivery.delivery.service.DeliveryService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes operations-only delivery actions (PIN lockout release). */
@RestController
@RequestMapping("/api/v1/ops/deliveries")
@PreAuthorize("hasAnyRole('OPS','ADMIN')")
public class OperationsDeliveryController {
    private final DeliveryService service;

    public OperationsDeliveryController(DeliveryService service) { this.service = service; }

    @PostMapping("/{deliveryId}/unlock-code")
    public DeliveryResponse unlockCode(@PathVariable UUID deliveryId) {
        return service.unlockCode(deliveryId);
    }
}
