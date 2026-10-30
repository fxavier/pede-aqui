package com.delivery.order.dto;

import com.delivery.delivery.api.DeliveryAddressResponse;
import com.delivery.order.entity.CancellationReason;
import com.delivery.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Order response returned after checkout, tracking, and list requests. {@code deliveryCode} and
 * {@code deliveryAddress} are filled only for the customer who owns the order. {@code cancellationReason}
 * says why a CANCELLED order ended (e.g. SELLER_REJECTED); null otherwise.
 */
public record OrderResponse(
        UUID id,
        String reference,
        OrderStatus status,
        BigDecimal total,
        String deliveryCode,
        String customerName,
        String vendorName,
        Instant createdAt,
        List<OrderItemResponse> items,
        DeliveryAddressResponse deliveryAddress,
        CancellationReason cancellationReason) {}
