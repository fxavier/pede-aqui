package com.delivery.order.controller;

import com.delivery.common.observability.BusinessMetrics;
import com.delivery.order.dto.CheckoutQuoteResponse;
import com.delivery.order.dto.CheckoutRequest;
import com.delivery.order.dto.CheckoutResult;
import com.delivery.order.dto.OrderResponse;
import com.delivery.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exposes checkout endpoint for active carts. */
@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutController {
    private static final Logger log = LoggerFactory.getLogger(CheckoutController.class);
    private final OrderService service;
    private BusinessMetrics metrics = BusinessMetrics.NOOP;

    public CheckoutController(OrderService service) { this.service = service; }

    /** Optional: slice tests without a meter registry keep the no-op metrics. */
    @Autowired(required = false)
    public void setBusinessMetrics(BusinessMetrics metrics) { this.metrics = metrics; }

    /**
     * 201 when the order is created; 200 with the same order when the idempotency key is replayed.
     * The legacy request shape without addressId gets a Deprecation header while the INC-12 window is open.
     * W6-A: optional quoteId → 409 quote_changed (expired / prices or fees moved, details.quote = fresh quote);
     * 422 out_of_service_area / address_too_far when the address is outside the vendor's area or distance.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> checkout(@Valid @RequestBody CheckoutRequest request) {
        // LAC-50: orders_checkout_total{result=created|replayed|rejected|error, reason} — counted after the commit.
        CheckoutResult result = metrics.recordOutcome(BusinessMetrics.CHECKOUT,
                () -> service.checkout(request.cartId(), request.idempotencyKey(), request.addressId(), request.quoteId()),
                checkout -> checkout.replayed() ? "replayed" : "created");
        ResponseEntity.BodyBuilder response = ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED);
        if (request.addressId() == null) {
            // Counted in logs to know when the last legacy caller is gone (no PII in the message).
            log.warn("Checkout without addressId accepted under the INC-12 legacy compatibility window");
            response.header("Deprecation", "true");
        }
        return response.body(result.order());
    }

    /**
     * Fees, VAT and total that checkout will charge for the cart and (optional) address — same pricing engine (INC-13).
     * W6-A: stores the quote with a TTL (app.checkout.quote-ttl, default 10 min) and returns quoteId + expiresAt.
     */
    @GetMapping("/quote")
    public CheckoutQuoteResponse quote(@RequestParam UUID cartId, @RequestParam(required = false) UUID addressId) {
        return service.quote(cartId, addressId);
    }
}
