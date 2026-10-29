package com.delivery.delivery.api;

import java.util.UUID;

/**
 * Called synchronously, in the caller's transaction, right after a delivery was retired (moved to CANCELLED before
 * pickup) so its active dispatch job is cancelled too and the courier is freed. Implemented by the dispatch module
 * (ADR-0002 C7: delivery no longer depends on dispatch; the job cancellation stays atomic with the retirement).
 */
public interface DeliveryRetirementListener {

    /** Cancels every active dispatch job of the delivery (idempotent). */
    void onDeliveryRetired(UUID tenantId, UUID deliveryId);
}
