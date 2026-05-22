package com.delivery.order.mapper;

import com.delivery.order.dto.OrderResponse;
import com.delivery.order.dto.TrackingResponse;
import com.delivery.order.entity.Order;
import org.springframework.stereotype.Component;

/** Converts order entities to API DTOs. */
@Component
public class OrderMapper {
    public OrderResponse toResponse(Order order) {
        return new OrderResponse(order.getId(), order.getReference(), order.getStatus(), order.getTotal(), order.getDeliveryConfirmationCodeDisplay());
    }

    public TrackingResponse toTrackingResponse(Order order) {
        return new TrackingResponse(order.getId(), order.getReference(), order.getStatus(), order.getDeliveryConfirmationCodeDisplay());
    }
}
