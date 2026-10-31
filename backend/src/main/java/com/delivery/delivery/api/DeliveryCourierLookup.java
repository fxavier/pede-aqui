package com.delivery.delivery.api;

import com.delivery.delivery.entity.Delivery;
import com.delivery.delivery.repository.DeliveryRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only lookup of the courier that worked an order's delivery (LAC-28, W7-D: the courier a customer may rate),
 * so other modules never read the delivery repository or entity. Tenant-scoped.
 */
@Component
public class DeliveryCourierLookup {
    private final DeliveryRepository deliveryRepository;

    public DeliveryCourierLookup(DeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    /** Courier id assigned to the order's delivery; empty when there is no delivery or no courier. */
    @Transactional(readOnly = true)
    public Optional<UUID> courierIdOfOrder(UUID tenantId, UUID orderId) {
        return deliveryRepository.findByTenantIdAndOrderId(tenantId, orderId).map(Delivery::getCourierId);
    }
}
