package com.delivery.order.entity;

/** Lifecycle of a customer cancellation request (LAC-26, V059): REQUESTED is the only open state. */
public enum CancellationRequestStatus {
    REQUESTED,
    APPROVED,
    REJECTED
}
