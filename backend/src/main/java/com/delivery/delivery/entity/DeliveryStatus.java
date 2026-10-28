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

    /**
     * True for a delivery that is still live but whose goods have not left the store: it is retired (moved to
     * CANCELLED, its active dispatch job cancelled) when the order is fully refunded or cancelled. Everything from
     * pickup on ({@link #isGoodsLeftTheStore}) and an already CANCELLED delivery is left alone.
     */
    public boolean isRetirableBeforePickup() {
        return this != CANCELLED && !isGoodsLeftTheStore();
    }
}
