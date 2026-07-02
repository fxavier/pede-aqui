package com.delivery.order.controller;

import com.delivery.order.dto.AdminOrderResponse;
import com.delivery.order.dto.OrderResponse;
import com.delivery.order.dto.TrackingResponse;
import com.delivery.order.service.OrderService;
import com.delivery.order.service.OrderTrackingService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes order listing and customer order tracking endpoints. */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderTrackingService trackingService;
    private final OrderService orderService;

    public OrderController(OrderTrackingService trackingService, OrderService orderService) {
        this.trackingService = trackingService;
        this.orderService = orderService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','VENDOR_ADMIN','VENDOR_STAFF')")
    public List<AdminOrderResponse> list() {
        return orderService.listForCurrentContext();
    }

    @GetMapping("/{orderId}/tracking")
    public TrackingResponse track(@PathVariable UUID orderId) { return trackingService.track(orderId); }
}
