package com.delivery.order.dto;

import com.delivery.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Order response returned after checkout, tracking, and list requests. */
public record OrderResponse(
        UUID id,
        String reference,
        OrderStatus status,
        BigDecimal total,
        String deliveryCode,
        String customerName,
        String vendorName,
        Instant createdAt,
        List<OrderItemResponse> items) {}
