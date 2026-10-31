package com.delivery.payment.controller;

import com.delivery.payment.dto.WebhookAckResponse;
import com.delivery.payment.service.PaymentWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payment provider callbacks (LAC-31 §5, W7-A). Anonymous on purpose (the PSP has no JWT): authenticity comes from the
 * provider's signature, verified before anything is read ({@code 401 invalid_webhook_signature}); idempotent by the
 * provider event id; rate-limited (group {@code payment-webhooks}). The raw body is taken as bytes because the signature
 * covers the exact bytes sent.
 */
@RestController
public class PaymentWebhookController {
    private final PaymentWebhookService service;

    public PaymentWebhookController(PaymentWebhookService service) {
        this.service = service;
    }

    @Operation(summary = "Payment provider callback (signed; idempotent by provider event id)")
    @PostMapping(value = "/api/v1/payments/webhooks/{provider}")
    public WebhookAckResponse receive(@PathVariable String provider, @RequestBody(required = false) byte[] body,
                                      HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        for (String name : Collections.list(request.getHeaderNames())) {
            headers.put(name.toLowerCase(Locale.ROOT), request.getHeader(name));
        }
        return new WebhookAckResponse(service.handle(provider.toLowerCase(Locale.ROOT), headers, body == null ? new byte[0] : body));
    }
}
