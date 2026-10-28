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
    CANCELLED;

    /**
     * True while a courier is working the delivery (assigned through arrived at the customer). In that window the
     * order's plain PIN must stay available: DeliveryService.complete checks only the delivery status, so the courier
     * may still finish with the PIN even after the order was fully refunded. Same set as the V042 data fix.
     */
    public boolean isCourierEngaged() {
        return switch (this) {
            case ASSIGNED, ACCEPTED, ARRIVED_AT_VENDOR, REASSIGNED, PICKED_UP, ON_ROUTE_TO_CUSTOMER, ARRIVED_AT_CUSTOMER -> true;
            default -> false;
        };
    }

    /**
     * True once the goods left the store (pickup and everything after it, including a failed delivery). From here
     * the order's stock reservation is consumed (OrderStockService.commit), never returned to available. Same set
     * as the V043 data fix.
     */
    public boolean isGoodsLeftTheStore() {
        return switch (this) {
            case PICKED_UP, ON_ROUTE_TO_CUSTOMER, ARRIVED_AT_CUSTOMER, DELIVERED, FAILED_DELIVERY -> true;
            default -> false;
        };
    }
}
