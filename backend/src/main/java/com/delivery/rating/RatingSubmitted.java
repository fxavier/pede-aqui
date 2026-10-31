package com.delivery.rating;

import java.time.Instant;
import java.util.UUID;

/**
 * Application event (LAC-28, W7-D): a customer submitted ({@code edited = false}) or edited ({@code edited = true}) the
 * rating of a delivered order. Published inside the rating transaction, after the vendor/courier aggregates were
 * recomputed in that same transaction; delivered after commit to {@code @ApplicationModuleListener}s (at-least-once —
 * listeners must be idempotent, e.g. keyed by {@code ratingId} + {@code occurredAt}). Ids and stars only: never the
 * comment or any personal data. Part of the rating module's public API. See docs/architecture/EVENT-CATALOG.md.
 */
public record RatingSubmitted(UUID tenantId, UUID ratingId, UUID orderId, UUID vendorId, UUID courierId,
                              int vendorStars, Integer courierStars, boolean edited, Instant occurredAt) {
}
