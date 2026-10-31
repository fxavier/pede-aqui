package com.delivery.payment.controller;

import com.delivery.payment.dto.ConfirmPaymentRequest;
import com.delivery.payment.dto.PaymentResponse;
import com.delivery.payment.service.PaymentService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Development-only manual payment confirmation (INC-01/AS-01, LAC-01). Not mapped at all under the {@code prod} profile
 * (W7-A): production payments are confirmed only by the payment provider (webhook / status query). Outside prod it is
 * still gated by {@code app.payments.mock-confirmation.enabled} (false → 404 for everyone).
 */
@RestController
@Profile("!prod")
@RequestMapping("/api/v1/payments")
public class MockPaymentConfirmationController {
    private final PaymentService service;

    public MockPaymentConfirmationController(PaymentService service) { this.service = service; }

    // No @PreAuthorize on purpose: when app.payments.mock-confirmation.enabled=false the endpoint must
    // answer 404 for every role, so the flag, role (CUSTOMER owner / OPS / ADMIN) and ownership checks
    // all live in PaymentService.confirm (INC-01, AS-01).
    @PostMapping("/{paymentId}/confirm")
    public PaymentResponse confirm(@PathVariable UUID paymentId, @Valid @RequestBody ConfirmPaymentRequest request) {
        return service.confirm(paymentId, request.idempotencyKey());
    }
}
