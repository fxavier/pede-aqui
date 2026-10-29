package com.delivery.delivery.api;

import com.delivery.delivery.entity.Delivery;
import com.delivery.delivery.entity.DeliveryEvent;
import com.delivery.delivery.entity.DeliveryStatus;
import com.delivery.delivery.repository.DeliveryEventRepository;
import com.delivery.delivery.repository.DeliveryRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retires the delivery of an order that will no longer be delivered (full refund, sales cancel, seller rejection)
 * while its goods are still in the store: the delivery moves to CANCELLED and its active dispatch job (if any) to
 * CANCELLED, which also frees the courier for new offers. From pickup on nothing is touched (the goods left).
 * Runs inside the caller's transaction, after the caller's payment lock and order read (lock order rule); a
 * concurrent courier update of the same delivery loses on the delivery @Version.
 *
 * <p>Part of the delivery API (ADR-0002 W3-A): order and sales call it synchronously because the retirement must be
 * atomic with the cancellation/refund. The dispatch-job side is done by the dispatch module through
 * {@link DeliveryRetirementListener}, in the same transaction, so delivery does not depend on dispatch.
 */
@Service
public class DeliveryRetirementService {
    static final String EVENT_RETIRED = "RETIRED";

    private final DeliveryRepository deliveryRepository;
    private final DeliveryRetirementListener retirementListener;
    private final DeliveryEventRepository eventRepository;

    public DeliveryRetirementService(DeliveryRepository deliveryRepository, DeliveryRetirementListener retirementListener,
                                     DeliveryEventRepository eventRepository) {
        this.deliveryRepository = deliveryRepository;
        this.retirementListener = retirementListener;
        this.eventRepository = eventRepository;
    }

    /** Retires the order's delivery (if any) when it is still before pickup; returns true when something changed. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean retireBeforePickup(UUID tenantId, UUID orderId, String reason) {
        return deliveryRepository.findByTenantIdAndOrderId(tenantId, orderId)
                .map(delivery -> retire(delivery, reason))
                .orElse(false);
    }

    /**
     * True when the order's delivery says the goods already left the store ({@link DeliveryStatus#isGoodsLeftTheStore}:
     * picked up or later, including DELIVERED and FAILED_DELIVERY); false when it did not or when the order has no
     * delivery row (legacy orders — callers combine this with the order status, V043 rule).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean isGoodsLeftTheStore(UUID tenantId, UUID orderId) {
        return deliveryRepository.findByTenantIdAndOrderId(tenantId, orderId)
                .map(Delivery::getStatus)
                .map(DeliveryStatus::isGoodsLeftTheStore)
                .orElse(false);
    }

    private boolean retire(Delivery delivery, String reason) {
        if (!delivery.getStatus().isRetirableBeforePickup()) {
            return false;
        }
        DeliveryStatus previous = delivery.getStatus();
        delivery.updateStatus(DeliveryStatus.CANCELLED);
        retirementListener.onDeliveryRetired(delivery.getTenantId(), delivery.getId());
        eventRepository.save(new DeliveryEvent(UUID.randomUUID(), delivery.getTenantId(), delivery.getId(), EVENT_RETIRED,
                "Delivery retired from " + previous + ": " + reason));
        return true;
    }
}
