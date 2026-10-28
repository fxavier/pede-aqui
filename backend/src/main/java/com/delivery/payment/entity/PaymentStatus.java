package com.delivery.payment.entity;

/** Supported payment lifecycle statuses for the MVP. */
public enum PaymentStatus {
    INITIATED,
    PENDING_CONFIRMATION,
    CONFIRMED,
    FAILED,
    CANCELLED,
    REFUND_PENDING,
    /** Some, but not all, of the amount has been refunded (approved refunds below the amount). */
    PARTIALLY_REFUNDED,
    /** Legacy value, never written by the application; kept so existing rows still load. Use FULLY_REFUNDED. */
    REFUNDED,
    /** Approved refunds cover the full payment amount. */
    FULLY_REFUNDED;

    /** Statuses of a payment whose money was captured (gross takings), whatever has been refunded since. */
    public static final java.util.Set<PaymentStatus> CAPTURED = java.util.EnumSet.of(CONFIRMED, PARTIALLY_REFUNDED, FULLY_REFUNDED);
}
