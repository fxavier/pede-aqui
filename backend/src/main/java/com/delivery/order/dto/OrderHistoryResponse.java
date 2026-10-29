package com.delivery.order.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /api/v1/orders/{orderId}/history} (W4-A): the order's status transitions and its payment's status
 * changes, oldest first. Orders created before V058 have no rows before the first transition after deploy.
 * {@code actorId} (Keycloak subject or job name) is null for merchant callers.
 */
public record OrderHistoryResponse(UUID orderId, String currentStatus, List<Entry> orderStatus, List<PaymentEntry> paymentStatus) {
    /** One order transition; {@code fromStatus} is null for the creation row. */
    public record Entry(String fromStatus, String toStatus, String actorType, String actorId, String reason,
                        String correlationId, Instant createdAt) {}

    /** One payment status change; {@code fromStatus} is null for the creation row. */
    public record PaymentEntry(UUID paymentId, String fromStatus, String toStatus, String actorType, String actorId,
                               String reason, String correlationId, Instant createdAt) {}
}
