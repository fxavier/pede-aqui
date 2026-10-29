package com.delivery.order.entity;

/** Why a cancellation request ended REJECTED (V059 closure_reason); null while open or when approved. */
public enum CancellationRequestClosure {
    /** OPS/ADMIN rejected the request; the order continues. */
    OPS_REJECTED,
    /** The order reached READY_FOR_PICKUP (or later) before a decision; the customer can no longer cancel it. */
    ORDER_ADVANCED,
    /** The order ended (CANCELLED/REFUNDED) through another path before a decision. */
    ORDER_CLOSED
}
