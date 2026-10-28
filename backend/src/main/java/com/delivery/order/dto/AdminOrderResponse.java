package com.delivery.order.dto;

import com.delivery.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Order response for admin/vendor contexts that excludes sensitive delivery confirmation codes. Of the
 * delivery address it carries only {@code deliveryArea} (the city of the checkout snapshot): enough for a
 * vendor to anticipate the route and preparation timing, without street, phone, recipient or GPS pin, which
 * only the assigned courier needs (GET /deliveries/{id}/address).
 */
public record AdminOrderResponse(
        UUID id,
        String reference,
        OrderStatus status,
        BigDecimal total,
        String customerName,
        String vendorName,
        Instant createdAt,
        List<OrderItemResponse> items,
        String deliveryArea) {}
