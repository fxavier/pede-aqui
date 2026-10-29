package com.delivery.dispatch.api;

import com.delivery.delivery.entity.Delivery;
import com.delivery.delivery.entity.DeliveryStatus;
import com.delivery.delivery.repository.DeliveryRepository;
import com.delivery.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Courier earnings (sum of the totals of the orders the courier delivered). Moved here from DashboardService
 * (ADR-0002 C4): the courier's own summary (CourierService) and the courier dashboard both read it, so dispatch owns
 * it and dashboard depends on dispatch, not the other way round.
 */
@Service
public class CourierEarnings {
    private final DeliveryRepository deliveryRepository;
    private final OrderRepository orderRepository;

    public CourierEarnings(DeliveryRepository deliveryRepository, OrderRepository orderRepository) {
        this.deliveryRepository = deliveryRepository;
        this.orderRepository = orderRepository;
    }

    /** Calculates total earnings for a courier based on delivered order totals. */
    @Transactional(readOnly = true)
    public BigDecimal calculate(UUID tenantId, UUID courierId) {
        return deliveryRepository.findByTenantIdAndCourierIdAndStatus(tenantId, courierId, DeliveryStatus.DELIVERED).stream()
                .map(Delivery::getOrderId)
                .map(orderId -> orderRepository.findByTenantIdAndId(tenantId, orderId))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(order -> order.getTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
