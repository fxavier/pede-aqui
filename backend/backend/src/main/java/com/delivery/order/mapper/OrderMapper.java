package com.delivery.order.mapper;

import com.delivery.order.dto.OrderItemResponse;
import com.delivery.order.dto.OrderResponse;
import com.delivery.order.dto.TrackingResponse;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderItem;
import java.util.List;
import org.springframework.stereotype.Component;

/** Converts order entities to API DTOs. */
@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {
        return new OrderResponse(order.getId(), order.getReference(), order.getStatus(),
                order.getTotal(), order.getDeliveryConfirmationCodeDisplay(),
                null, null, null, null);
    }

    public OrderResponse toResponse(Order order, String customerName, String vendorName) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(this::toItemResponse)
                .toList();
        return new OrderResponse(order.getId(), order.getReference(), order.getStatus(),
                order.getTotal(), order.getDeliveryConfirmationCodeDisplay(),
                customerName, vendorName, order.getCreatedAt(), items);
    }

    public OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(item.getId(), item.getProductNameSnapshot(),
                item.getSkuNameSnapshot(), item.getUnitPriceSnapshot(),
                item.getQuantity(), item.getLineTotal());
    }

    public TrackingResponse toTrackingResponse(Order order) {
        return new TrackingResponse(order.getId(), order.getReference(), order.getStatus(), order.getDeliveryConfirmationCodeDisplay());
    }
}
