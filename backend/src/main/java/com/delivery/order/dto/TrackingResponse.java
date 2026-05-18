package com.delivery.order.dto;

import com.delivery.order.entity.OrderStatus;
import java.util.UUID;

/** Status-based tracking response for the MVP. */
public record TrackingResponse(UUID orderId, String reference, OrderStatus orderStatus, String deliveryCode) {}
