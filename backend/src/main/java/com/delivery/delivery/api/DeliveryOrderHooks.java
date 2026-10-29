package com.delivery.delivery.api;

import java.util.Optional;
import java.util.UUID;

/**
 * What the courier delivery lifecycle needs from the delivery's order, called synchronously inside the delivery
 * transaction (ADR-0002 C2). Declared here and implemented by the order module, so delivery does not depend on order
 * while the order transition, the stock lifecycle (INC-07) and the delivery status still commit or roll back
 * together. Every method loads the order tenant-scoped and answers 404 "Order was not found" when it is missing.
 */
public interface DeliveryOrderHooks {

    /**
     * Refuses (409 {@code order_not_deliverable}) when the order was refunded or cancelled: the goods must not leave
     * the store or be handed over for an order whose sale was undone. Called before any mutation.
     */
    void requireDeliverable(UUID tenantId, UUID orderId);

    /**
     * Courier picked the goods up: the order follows (ASSIGNED_TO_COURIER -> PICKED_UP, W4-A) and its stock
     * reservation is committed (no-op if already committed).
     */
    void onPickedUp(UUID tenantId, UUID orderId);

    /** Courier on the way to / arrived at the customer: the order becomes DELIVERING when it is still before that. */
    void onOutForDelivery(UUID tenantId, UUID orderId);

    /** Delivery completed with the customer PIN: order DELIVERED (clears the plain PIN) and stock committed. */
    void onDelivered(UUID tenantId, UUID orderId);

    /** Delivery failed at the customer: order CANCELLED and its reservation released (no-op once committed). */
    void onDeliveryFailed(UUID tenantId, UUID orderId);

    /** The order's checkout address snapshot; empty when the order was placed without one. */
    Optional<DeliveryAddressResponse> deliveryAddress(UUID tenantId, UUID orderId);
}
