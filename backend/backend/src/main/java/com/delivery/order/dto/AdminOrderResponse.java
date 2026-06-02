package com.delivery.order.dto;

import com.delivery.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Order response for admin/vendor contexts that excludes sensitive delivery confirmation codes. */
public record AdminOrderResponse(
        UUID id,
        String reference,
        OrderStatus status,
        BigDecimal total,
        String customerName,
        String vendorName,
        Instant createdAt,
        List<OrderItemResponse> items) {}