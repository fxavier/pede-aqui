package com.delivery.order.controller;

import com.delivery.order.dto.OrderResponse;
import com.delivery.order.dto.RejectOrderRequest;
import com.delivery.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes vendor fulfillment transitions for order processing. */
@RestController
@RequestMapping("/api/v1/vendor/orders")
public class VendorOrderController {
    private final OrderService orderService;

    public VendorOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PatchMapping("/{orderId}/accept")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN','VENDOR_STAFF')")
    public OrderResponse accept(@PathVariable UUID orderId) {
        return orderService.acceptByVendor(orderId);
    }

    @PatchMapping("/{orderId}/reject")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN','VENDOR_STAFF')")
    public OrderResponse reject(@PathVariable UUID orderId, @Valid @RequestBody RejectOrderRequest request) {
        return orderService.rejectByVendor(orderId, request.reason());
    }

    @PatchMapping("/{orderId}/preparing")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN','VENDOR_STAFF')")
    public OrderResponse preparing(@PathVariable UUID orderId) {
        return orderService.markPreparing(orderId);
    }

    @PatchMapping("/{orderId}/ready-for-pickup")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN','VENDOR_STAFF')")
    public OrderResponse readyForPickup(@PathVariable UUID orderId) {
        return orderService.markReadyForPickup(orderId);
    }
}
