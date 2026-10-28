package com.delivery.order.controller;

import com.delivery.order.dto.CheckoutRequest;
import com.delivery.order.dto.CheckoutResult;
import com.delivery.order.dto.OrderResponse;
import com.delivery.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes checkout endpoint for active carts. */
@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutController {
    private final OrderService service;

    public CheckoutController(OrderService service) { this.service = service; }

    /** 201 when the order is created; 200 with the same order when the idempotency key is replayed. */
    @PostMapping
    public ResponseEntity<OrderResponse> checkout(@Valid @RequestBody CheckoutRequest request) {
        CheckoutResult result = service.checkout(request.cartId(), request.idempotencyKey());
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result.order());
    }
}
