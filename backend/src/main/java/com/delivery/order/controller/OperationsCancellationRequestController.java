package com.delivery.order.controller;

import com.delivery.common.pagination.PageLimits;
import com.delivery.common.pagination.PageResponse;
import com.delivery.order.dto.CancellationRequestResponse;
import com.delivery.order.entity.CancellationRequestStatus;
import com.delivery.order.service.CustomerCancellationService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** OPS/ADMIN queue of customer cancellation requests (LAC-26): list, approve (cancel with fee), reject — tenant-scoped, audited. */
@RestController
@RequestMapping("/api/v1/ops/cancellation-requests")
@PreAuthorize("hasAnyRole('OPS','ADMIN')")
public class OperationsCancellationRequestController {
    private final CustomerCancellationService service;

    public OperationsCancellationRequestController(CustomerCancellationService service) { this.service = service; }

    /** Offset page {@code { content, page, size, totalElements }} of requests in {@code status} (default REQUESTED), newest first. */
    @GetMapping
    public PageResponse<CancellationRequestResponse> list(
            @RequestParam(required = false) CancellationRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageLimits.DEFAULT_PAGE_SIZE) int size) {
        return service.list(status, page, size);
    }

    /** Approves the request: the order is cancelled and refunded minus the fee; 409 order_not_cancellable if it moved on. */
    @PostMapping("/{requestId}/approve")
    public CancellationRequestResponse approve(@PathVariable UUID requestId) {
        return service.approve(requestId);
    }

    /** Rejects the request; the order continues. */
    @PostMapping("/{requestId}/reject")
    public CancellationRequestResponse reject(@PathVariable UUID requestId) {
        return service.reject(requestId);
    }
}
