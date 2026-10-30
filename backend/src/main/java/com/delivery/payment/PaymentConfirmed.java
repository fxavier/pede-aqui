package com.delivery.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Application event (LAC-33, W6-B): a payment's money was captured ({@code PENDING_CONFIRMATION -> CONFIRMED}).
 * Published by {@code PaymentService.confirm} inside the confirming transaction and delivered after commit to
 * {@code @ApplicationModuleListener}s through the JDBC event publication registry (at-least-once — listeners must be
 * idempotent; natural key {@code paymentId}). Ids, amount and instant only, never personal data.
 * See docs/architecture/EVENT-CATALOG.md.
 */
public record PaymentConfirmed(UUID tenantId, UUID paymentId, UUID orderId, BigDecimal amount, String currency,
                               Instant confirmedAt) {
}
