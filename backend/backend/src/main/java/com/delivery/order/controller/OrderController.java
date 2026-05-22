package com.delivery.order.controller;

import com.delivery.order.dto.TrackingResponse;
import com.delivery.order.service.OrderTrackingService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes customer order tracking endpoints. */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderTrackingService trackingService;

    public OrderController(OrderTrackingService trackingService) { this.trackingService = trackingService; }

    @GetMapping("/{orderId}/tracking")
    public TrackingResponse track(@PathVariable UUID orderId) { return trackingService.track(orderId); }
}
