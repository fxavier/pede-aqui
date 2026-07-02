package com.delivery.payment.controller;

import com.delivery.payment.dto.ConfirmPaymentRequest;
import com.delivery.payment.dto.PaymentResponse;
import com.delivery.payment.dto.RefundRequest;
import com.delivery.payment.dto.RefundResponse;
import com.delivery.payment.service.PaymentService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes local/mock payment endpoints. */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService service;

    public PaymentController(PaymentService service) { this.service = service; }

    @PostMapping("/{paymentId}/confirm")
    public PaymentResponse confirm(@PathVariable UUID paymentId, @Valid @RequestBody ConfirmPaymentRequest request) {
        return service.confirm(paymentId, request.idempotencyKey());
    }

    @PostMapping("/{paymentId}/refunds")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    public RefundResponse requestRefund(@PathVariable UUID paymentId, @Valid @RequestBody RefundRequest request) {
        return service.requestRefund(paymentId, request);
    }

    @PostMapping("/refunds/{refundId}/approve")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    public RefundResponse approveRefund(@PathVariable UUID refundId) { return service.approveRefund(refundId); }

    @PostMapping("/refunds/{refundId}/reject")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    public RefundResponse rejectRefund(@PathVariable UUID refundId) { return service.rejectRefund(refundId); }
}
