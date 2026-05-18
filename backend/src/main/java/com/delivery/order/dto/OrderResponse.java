package com.delivery.order.dto;

import com.delivery.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.util.UUID;

/** Order response returned after checkout and tracking requests. */
public record OrderResponse(UUID id, String reference, OrderStatus status, BigDecimal total, String deliveryCode) {}
