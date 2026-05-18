package com.delivery.order.entity;

/** Supported order lifecycle statuses for the MVP. */
public enum OrderStatus {
    PENDING,
    PAYMENT_PENDING,
    PAYMENT_CONFIRMED,
    ACCEPTED_BY_VENDOR,
    REJECTED_BY_VENDOR,
    PREPARING,
    READY_FOR_PICKUP,
    DISPATCH_PENDING,
    ASSIGNED_TO_COURIER,
    PICKED_UP,
    DELIVERING,
    DELIVERED,
    CANCELLED,
    REFUND_PENDING,
    REFUNDED
}
