package com.delivery.order.dto;

import com.delivery.order.entity.CancellationReason;
import com.delivery.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Result of POST /api/v1/me/orders/{id}/cancel (LAC-26). {@code outcome} CANCELLED (200: the order is cancelled —
 * now or by an earlier call/another path) or REQUESTED (202: a cancellation request awaits OPS; {@code request} holds
 * it with the quoted fee). {@code refundAmount} is the refund requested by the customer's cancellation (null when
 * nothing was captured or the order was cancelled by another path).
 */
public record CustomerCancellationResponse(
        UUID orderId,
        OrderStatus orderStatus,
        CancellationReason cancellationReason,
        Outcome outcome,
        BigDecimal refundAmount,
        CancellationRequestResponse request) {

    /** What the call achieved. */
    public enum Outcome { CANCELLED, REQUESTED }
}
