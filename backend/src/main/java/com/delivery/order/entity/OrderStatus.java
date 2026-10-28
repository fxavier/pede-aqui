package com.delivery.order.entity;

/** Supported order lifecycle statuses for the MVP. */
public enum OrderStatus {
    PENDING,
    PAYMENT_PENDING,
    PAYMENT_CONFIRMED,
    ACCEPTED_BY_VENDOR,
    /** Legacy: no longer written. A seller rejection is CANCELLED with cancellation_reason SELLER_REJECTED; kept for existing rows. */
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
