package com.delivery.payment.controller;

import com.delivery.payment.dto.CreatePaymentAttemptRequest;
import com.delivery.payment.dto.OrderPaymentResponse;
import com.delivery.payment.dto.PaymentAttemptResponse;
import com.delivery.payment.service.PaymentAttemptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer payment through the payment provider (LAC-31, W7-A): look up the order's payment, start an attempt (202 —
 * the customer confirms on the phone) and poll its state. Supports the optional {@code Idempotency-Key} header (LAC-24).
 */
@RestController
public class PaymentAttemptController {
    private final PaymentAttemptService service;

    public PaymentAttemptController(PaymentAttemptService service) {
        this.service = service;
    }

    @Operation(summary = "Start a mobile-money payment attempt for the caller's pending payment (202; poll the attempt)")
    @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = false,
            description = "Optional (8-128 chars [A-Za-z0-9_.:-]); a repeat with the same key and body replays the first response")
    @PostMapping("/api/v1/payments/{paymentId}/attempts")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PaymentAttemptResponse create(@PathVariable UUID paymentId, @Valid @RequestBody CreatePaymentAttemptRequest request) {
        return service.create(paymentId, request.msisdn());
    }

    @Operation(summary = "State of one payment attempt (owning customer, or OPS/FINANCE/ADMIN)")
    @GetMapping("/api/v1/payments/{paymentId}/attempts/{attemptId}")
    @PreAuthorize("hasAnyRole('CUSTOMER','OPS','FINANCE','ADMIN')")
    public PaymentAttemptResponse get(@PathVariable UUID paymentId, @PathVariable UUID attemptId) {
        return service.get(paymentId, attemptId);
    }

    @Operation(summary = "The caller's order payment: payment id, whether it can be paid now and the latest attempt")
    @GetMapping("/api/v1/me/orders/{orderId}/payment")
    @PreAuthorize("hasRole('CUSTOMER')")
    public OrderPaymentResponse forOrder(@PathVariable UUID orderId) {
        return service.forOrder(orderId);
    }
}
