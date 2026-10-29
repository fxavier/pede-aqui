package com.delivery.order.controller;

import com.delivery.common.pagination.CursorPage;
import com.delivery.common.pagination.PageLimits;
import com.delivery.order.dto.CancelOrderRequest;
import com.delivery.order.dto.CustomerCancellationResponse;
import com.delivery.order.dto.CustomerOrderDetailResponse;
import com.delivery.order.dto.CustomerOrderSummaryResponse;
import com.delivery.order.service.CustomerCancellationService;
import com.delivery.order.service.CustomerOrderQueryService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The authenticated customer's own order history, order detail (LAC-21) and cancellation (LAC-26); CUSTOMER role only. */
@RestController
@RequestMapping("/api/v1/me/orders")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerOrderController {
    private final CustomerOrderQueryService service;
    private final CustomerCancellationService cancellationService;

    public CustomerOrderController(CustomerOrderQueryService service, CustomerCancellationService cancellationService) {
        this.service = service;
        this.cancellationService = cancellationService;
    }

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

    /**
     * Cancels one of the caller's orders (LAC-26): 200 + outcome CANCELLED (free phase, or already cancelled), 202 +
     * outcome REQUESTED (accepted/preparing: a request awaits OPS), 409 order_not_cancellable from READY_FOR_PICKUP on,
     * 404 when the order is not the caller's. Idempotent: repeats return the same state without new effects.
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<CustomerCancellationResponse> cancel(@PathVariable UUID orderId,
                                                               @Valid @RequestBody(required = false) CancelOrderRequest body) {
        CustomerCancellationResponse result = cancellationService.cancelMine(orderId, body == null ? null : body.reason());
        HttpStatus status = result.outcome() == CustomerCancellationResponse.Outcome.REQUESTED ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }
}
