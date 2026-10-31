package com.delivery.order.entity;

/** Lifecycle of a delivery incident (V070): opened by FAILED_DELIVERY, resolved once by OPS/ADMIN. */
public enum DeliveryIncidentStatus {
    OPEN,
    RESOLVED
}
