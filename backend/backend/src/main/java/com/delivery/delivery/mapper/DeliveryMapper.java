package com.delivery.delivery.mapper;

import com.delivery.delivery.dto.DeliveryResponse;
import com.delivery.delivery.entity.Delivery;
import org.springframework.stereotype.Component;

/** Converts delivery entities to API DTOs. */
@Component
public class DeliveryMapper {
    public DeliveryResponse toResponse(Delivery delivery) {
        return new DeliveryResponse(delivery.getId(), delivery.getOrderId(), delivery.getStatus(), delivery.getConfirmationAttempts());
    }
}
