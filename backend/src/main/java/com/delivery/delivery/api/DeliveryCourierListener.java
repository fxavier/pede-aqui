package com.delivery.delivery.api;

import java.util.UUID;

/**
 * Called synchronously, in the delivery transaction, when a delivery the courier was working reached a terminal
 * status (DELIVERED with the PIN, FAILED_DELIVERY), so the courier's operational status can go BUSY -> AVAILABLE
 * (LAC-42). Implemented by the dispatch module, which owns couriers (same inversion as
 * {@link DeliveryRetirementListener}; retirements free the courier through that hook instead).
 */
public interface DeliveryCourierListener {

    /** The courier finished the delivery; release them if they hold no other active delivery (idempotent). */
    void onCourierFreed(UUID tenantId, UUID courierId);

    /** Does nothing; the default for services built without the dispatch module (unit and slice tests). */
    DeliveryCourierListener NOOP = (tenantId, courierId) -> { };
}
