package com.delivery.order.dto;

/** Checkout outcome: the order plus whether it was replayed from an earlier request with the same idempotency key. */
public record CheckoutResult(OrderResponse order, boolean replayed) {}
