package com.delivery.order.entity;

/** Refund decided for a failed delivery (LAC-44). There is no "no refund" outcome: the goods were never handed over. */
public enum IncidentOutcome {
    /** The whole remaining refundable amount goes back to the customer. */
    REFUND_FULL,
    /** Refund of the remainder minus the order's delivery fee, which is retained (customer fault). */
    REFUND_MINUS_DELIVERY_FEE
}
