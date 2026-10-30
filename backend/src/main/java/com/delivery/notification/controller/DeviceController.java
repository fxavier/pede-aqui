package com.delivery.notification.controller;

import com.delivery.notification.dto.DeviceResponse;
import com.delivery.notification.dto.RegisterDeviceRequest;
import com.delivery.notification.dto.UnregisterDeviceRequest;
import com.delivery.notification.service.DeviceTokenService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's push devices (LAC-48): any authenticated user with a profile in the tenant. */
@RestController
@RequestMapping("/api/v1/me/devices")
@PreAuthorize("isAuthenticated()")
public class DeviceController {
    private final DeviceTokenService deviceTokenService;

    public DeviceController(DeviceTokenService deviceTokenService) {
        this.deviceTokenService = deviceTokenService;
    }

    /** Registers or refreshes a push token (idempotent; 200 with the device, token masked). */
    @PostMapping
    public DeviceResponse register(@Valid @RequestBody RegisterDeviceRequest request) {
        return deviceTokenService.register(request);
    }

    /** Unregisters a push token (apps call it on logout); 204 whether or not it was registered. */
    @DeleteMapping
    public ResponseEntity<Void> unregister(@Valid @RequestBody UnregisterDeviceRequest request) {
        deviceTokenService.unregister(request.token());
        return ResponseEntity.noContent().build();
    }
}
