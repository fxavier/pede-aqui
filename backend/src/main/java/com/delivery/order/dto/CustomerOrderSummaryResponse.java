package com.delivery.order.dto;

import com.delivery.order.entity.CancellationReason;
import com.delivery.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the customer's order history (GET /api/v1/me/orders, LAC-21). No PIN and no address here: those are
 * only on the detail. {@code cancellationReason} tells a seller rejection apart from other cancellations.
 */
public record CustomerOrderSummaryResponse(
        UUID id,
        String reference,
        OrderStatus status,
        CancellationReason cancellationReason,
        UUID vendorId,
        String vendorName,
        int itemCount,
        BigDecimal subtotal,
        BigDecimal fees,
        BigDecimal taxes,
        BigDecimal discountTotal,
        BigDecimal total,
        Instant createdAt) {}
