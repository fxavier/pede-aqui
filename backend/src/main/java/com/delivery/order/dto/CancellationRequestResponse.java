package com.delivery.order.dto;

import com.delivery.order.entity.CancellationRequestClosure;
import com.delivery.order.entity.CancellationRequestStatus;
import com.delivery.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A customer cancellation request (LAC-26). {@code feeAmount} is the quoted fee while REQUESTED and the fee actually
 * withheld once APPROVED; {@code refundAmount} is set on approval; {@code closureReason} says why a REJECTED request
 * ended (OPS_REJECTED, ORDER_ADVANCED, ORDER_CLOSED). {@code orderReference}/{@code orderStatus} are filled in the
 * OPS queue and may be null elsewhere.
 */
public record CancellationRequestResponse(
        UUID id,
        UUID orderId,
        String orderReference,
        OrderStatus orderStatus,
        CancellationRequestStatus status,
        CancellationRequestClosure closureReason,
        String reason,
        BigDecimal feeRate,
        BigDecimal feeAmount,
        BigDecimal refundAmount,
        Instant requestedAt,
        Instant decidedAt) {}
