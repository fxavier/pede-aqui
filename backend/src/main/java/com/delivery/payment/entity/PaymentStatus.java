package com.delivery.payment.entity;

/** Supported payment lifecycle statuses for the MVP. */
public enum PaymentStatus {
    INITIATED,
    PENDING_CONFIRMATION,
    CONFIRMED,
    FAILED,
    CANCELLED,
    REFUND_PENDING,
    PARTIALLY_REFUNDED,
    REFUNDED
}
