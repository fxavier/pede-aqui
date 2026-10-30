package com.delivery.order.controller;

import com.delivery.common.pagination.CursorPage;
import com.delivery.common.pagination.PageLimits;
import com.delivery.order.dto.AdminOrderPageResponse;
import com.delivery.order.dto.AdminOrderResponse;
import com.delivery.order.dto.OrderResponse;
import com.delivery.order.dto.TrackingResponse;
import com.delivery.order.service.OrderListingService;
import com.delivery.order.service.OrderTrackingService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exposes order listing and customer order tracking endpoints. */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderTrackingService trackingService;
    private final OrderListingService listingService;

    public OrderController(OrderTrackingService trackingService, OrderListingService listingService) {
        this.trackingService = trackingService;
        this.listingService = listingService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPS','VENDOR_ADMIN','VENDOR_STAFF')")
    public ResponseEntity<List<AdminOrderResponse>> list() {
        // LAC-14: newest 200, X-Truncated when capped. W3-D: a platform admin sending X-Tenant-Id sees only that tenant.
        return listingService.listForCurrentContext().toResponse();
    }

    /** Keyset variant of {@link #list()} (LAC-14): {@code { items, nextCursor }}, limit 1..200 (default 50); same scoping. */
    @GetMapping("/cursor")
    @PreAuthorize("hasAnyRole('ADMIN','OPS','VENDOR_ADMIN','VENDOR_STAFF')")
    public CursorPage<AdminOrderResponse> listCursor(
            @RequestParam(defaultValue = "" + PageLimits.DEFAULT_CURSOR_LIMIT) int limit,
            @RequestParam(required = false) String cursor) {
        return listingService.listCursorForCurrentContext(limit, cursor);
    }

    /** Paginated variant of {@link #list()} (newest first, size capped at 100); same role and vendor scoping. */
    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('ADMIN','OPS','VENDOR_ADMIN','VENDOR_STAFF')")
    public AdminOrderPageResponse listPage(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return listingService.listPageForCurrentContext(page, size);
    }

    @GetMapping("/{orderId}/tracking")
    public TrackingResponse track(@PathVariable UUID orderId) { return trackingService.track(orderId); }
}
