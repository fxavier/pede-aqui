package com.delivery.order.entity;

/** Why an order ended in CANCELLED; null on orders cancelled through paths that do not record a reason. */
public enum CancellationReason {
    /** The seller (vendor) rejected the order; the eligible remainder of the payment is refunded. */
    SELLER_REJECTED
}
