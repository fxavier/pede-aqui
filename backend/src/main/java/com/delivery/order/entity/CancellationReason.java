package com.delivery.order.entity;

/** Why an order ended in CANCELLED; null on orders cancelled through paths that do not record a reason. */
public enum CancellationReason {
    /** The seller rejected the order after a refund had already been requested or completed on its payment. */
    SELLER_REJECTED
}
