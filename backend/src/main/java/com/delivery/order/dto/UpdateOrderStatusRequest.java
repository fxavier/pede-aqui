package com.delivery.order.dto;

import com.delivery.order.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

/** Request used by authorized users to move an order through valid states. */
public record UpdateOrderStatusRequest(@NotNull OrderStatus status) {}
