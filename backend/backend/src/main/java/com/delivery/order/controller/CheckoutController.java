package com.delivery.order.controller;

import com.delivery.order.dto.CheckoutRequest;
import com.delivery.order.dto.OrderResponse;
import com.delivery.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Exposes checkout endpoint for active carts. */
@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutController {
    private final OrderService service;

    public CheckoutController(OrderService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(@Valid @RequestBody CheckoutRequest request) { return service.checkout(request.cartId(), request.idempotencyKey()); }
}
