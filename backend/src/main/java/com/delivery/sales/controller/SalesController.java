package com.delivery.sales.controller;

import com.delivery.payment.dto.RefundResponse;
import com.delivery.sales.dto.SaleDetailResponse;
import com.delivery.sales.dto.SalesActionResponse;
import com.delivery.sales.dto.SalesCancelRequest;
import com.delivery.sales.dto.SalesPageResponse;
import com.delivery.sales.dto.SalesRefundRequest;
import com.delivery.sales.dto.SalesResendNotificationRequest;
import com.delivery.sales.dto.SalesSearchFilter;
import com.delivery.sales.dto.SalesStatusOverrideRequest;
import com.delivery.sales.service.SalesService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the commercial sales lens over orders and its guarded operational actions (Spec 002 US-5/US-6). */
@RestController
@RequestMapping("/api/v1/sales/orders")
public class SalesController {
    private final SalesService salesService;

    public SalesController(SalesService salesService) {
        this.salesService = salesService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPS','FINANCE','VENDOR_ADMIN','SUPPORT')")
    public SalesPageResponse search(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID skuId,
            @RequestParam(required = false) String paymentProvider,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return salesService.search(new SalesSearchFilter(from, to, status, vendorId, productId, skuId, paymentProvider, q, page, size));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPS','FINANCE','VENDOR_ADMIN','SUPPORT')")
    public SaleDetailResponse detail(@PathVariable UUID orderId) {
        return salesService.detail(orderId);
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','OPS','SUPPORT','VENDOR_ADMIN')")
    public SalesActionResponse cancel(@PathVariable UUID orderId, @Valid @RequestBody SalesCancelRequest request) {
        return salesService.cancel(orderId, request.reason());
    }

    @PostMapping("/{orderId}/refund")
    @PreAuthorize("hasAnyRole('ADMIN','OPS','FINANCE')")
    public RefundResponse refund(
            @PathVariable UUID orderId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SalesRefundRequest request) {
        return salesService.refund(orderId, request.amount(), request.reason(), idempotencyKey);
    }

    @PostMapping("/{orderId}/resend-notification")
    @PreAuthorize("hasAnyRole('ADMIN','OPS','SUPPORT','VENDOR_ADMIN')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendNotification(@PathVariable UUID orderId, @Valid @RequestBody SalesResendNotificationRequest request) {
        salesService.resendNotification(orderId, request.type());
    }

    @PostMapping("/{orderId}/status-override")
    @PreAuthorize("hasAnyRole('ADMIN','OPS')")
    public SalesActionResponse statusOverride(
            @PathVariable UUID orderId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SalesStatusOverrideRequest request) {
        return salesService.statusOverride(orderId, request.targetStatus(), request.reason());
    }
}
