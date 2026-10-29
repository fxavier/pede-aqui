package com.delivery.order.entity;

/** Why an order ended in CANCELLED; null on orders cancelled through paths that do not record a reason. */
public enum CancellationReason {
    /** The seller (vendor) rejected the order; the eligible remainder of the payment is refunded. */
    SELLER_REJECTED,
    /** PaymentExpiryJob (W4-A): the payment stayed pending longer than app.orders.payment-timeout; nothing was captured. */
    PAYMENT_EXPIRED,
    /** AcceptanceTimeoutJob (W4-A): the vendor did not accept within app.orders.acceptance-timeout; the remainder is refunded. */
    VENDOR_TIMEOUT
}
