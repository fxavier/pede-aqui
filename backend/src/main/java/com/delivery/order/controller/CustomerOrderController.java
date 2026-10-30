package com.delivery.order.controller;

import com.delivery.common.pagination.CursorPage;
import com.delivery.common.pagination.PageLimits;
import com.delivery.order.dto.CustomerOrderDetailResponse;
import com.delivery.order.dto.CustomerOrderSummaryResponse;
import com.delivery.order.service.CustomerOrderQueryService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The authenticated customer's own order history and order detail (LAC-21); CUSTOMER role only. */
@RestController
@RequestMapping("/api/v1/me/orders")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerOrderController {
    private final CustomerOrderQueryService service;

    public CustomerOrderController(CustomerOrderQueryService service) { this.service = service; }

    /** Keyset page {@code { items, nextCursor }} of the caller's orders, newest first; limit 1..200 (default 50). */
    @GetMapping
    public CursorPage<CustomerOrderSummaryResponse> list(
            @RequestParam(defaultValue = "" + PageLimits.DEFAULT_CURSOR_LIMIT) int limit,
            @RequestParam(required = false) String cursor) {
        return service.listMine(limit, cursor);
    }

    /** One of the caller's orders; 404 when it does not exist or belongs to someone else. */
    @GetMapping("/{orderId}")
    public CustomerOrderDetailResponse get(@PathVariable UUID orderId) {
        return service.getMine(orderId);
    }
}
