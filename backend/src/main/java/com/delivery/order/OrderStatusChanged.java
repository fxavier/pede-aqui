package com.delivery.order;

import java.time.Instant;
import java.util.UUID;

/**
 * Application event (LAC-47, ADR-0002): an order moved from one status to another. Published inside the transaction
 * that changed the order and delivered after commit to {@code @ApplicationModuleListener}s through the JDBC event
 * publication registry (at-least-once — listeners must be idempotent). Part of the order module's public API (base
 * package). Carries ids and status names only, never personal data. See docs/architecture/EVENT-CATALOG.md.
 *
 * <p>Currently published by the vendor fulfilment transitions only (accept, preparing, ready for pickup).
 */
public record OrderStatusChanged(UUID tenantId, UUID orderId, UUID vendorId, String previousStatus, String newStatus,
                                 Instant occurredAt) {
}
