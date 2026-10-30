package com.delivery.order.dto;

import com.delivery.common.pricing.DeliveryFeeBasis;
import com.delivery.delivery.api.DeliveryAddressResponse;
import com.delivery.order.entity.CancellationReason;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.service.CustomerCancellationPolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The customer's own order (GET /api/v1/me/orders/{id}, LAC-21): items, the checkout address snapshot, the totals
 * and the pricing-quote breakdown. {@code deliveryCode} is the delivery PIN while the delivery is active and null
 * once the order is delivered, cancelled or refunded. {@code deliveryFee}, {@code serviceFee},
 * {@code deliveryFeeBasis} and {@code distanceKm} are null for orders placed before the pricing quote (INC-13);
 * {@code deliveryAddress} is null for orders placed without an address (pre-INC-12).
 *
 * <p>Cancellation (LAC-26): {@code cancellationPhase} = FREE (cancel now), REQUEST (ask OPS; {@code
 * cancellationFeeEstimate} is the fee that would be withheld), ALREADY_CANCELLED or NOT_CANCELLABLE;
 * {@code cancellationRequest} is the order's most recent request (null when none).
 */
public record CustomerOrderDetailResponse(
        UUID id,
        String reference,
        OrderStatus status,
        CancellationReason cancellationReason,
        UUID vendorId,
        String vendorName,
        Instant createdAt,
        List<OrderItemResponse> items,
        DeliveryAddressResponse deliveryAddress,
        String deliveryCode,
        BigDecimal subtotal,
        BigDecimal fees,
        BigDecimal deliveryFee,
        BigDecimal serviceFee,
        BigDecimal taxes,
        BigDecimal discountTotal,
        BigDecimal total,
        DeliveryFeeBasis deliveryFeeBasis,
        BigDecimal distanceKm,
        CustomerCancellationPolicy.Phase cancellationPhase,
        BigDecimal cancellationFeeEstimate,
        CancellationRequestResponse cancellationRequest) {}
