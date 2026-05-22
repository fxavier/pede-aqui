package com.delivery.delivery.entity;

/** Supported delivery lifecycle statuses for the MVP. */
public enum DeliveryStatus {
    DISPATCH_PENDING,
    ASSIGNED,
    ACCEPTED,
    REJECTED,
    ARRIVED_AT_VENDOR,
    PICKED_UP,
    ON_ROUTE_TO_CUSTOMER,
    ARRIVED_AT_CUSTOMER,
    DELIVERED,
    FAILED_DELIVERY,
    REASSIGNED,
    CANCELLED
}
