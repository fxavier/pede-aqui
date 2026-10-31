/**
 * Public API of the delivery module (ADR-0002 C2/C7). Synchronous entry points for the modules above it
 * ({@link com.delivery.delivery.api.DeliveryRetirementService}, {@link com.delivery.delivery.api.DeliveryAddressResponse},
 * {@link com.delivery.delivery.api.DeliveryCourierLookup} — LAC-28)
 * and the hooks delivery calls inside its own transactions but does not implement:
 * {@link com.delivery.delivery.api.DeliveryOrderHooks} (implemented by order) and
 * {@link com.delivery.delivery.api.AssignedCourierResolver} / {@link com.delivery.delivery.api.DeliveryRetirementListener} /
 * {@link com.delivery.delivery.api.DeliveryCourierListener} (LAC-42)
 * (implemented by dispatch), plus the failure types handed to order on FAILED_DELIVERY
 * ({@link com.delivery.delivery.api.DeliveryFailure}, {@link com.delivery.delivery.api.DeliveryFailureReason},
 * {@link com.delivery.delivery.api.CourierPosition}; LAC-44). The hooks invert the dependency so delivery depends on neither order nor dispatch while
 * every cross-module write stays in the same transaction (see ADR-0002 implementation notes).
 */
@NamedInterface("api")
package com.delivery.delivery.api;

import org.springframework.modulith.NamedInterface;
