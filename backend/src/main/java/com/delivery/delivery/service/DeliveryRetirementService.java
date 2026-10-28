package com.delivery.delivery.service;

import com.delivery.delivery.entity.Delivery;
import com.delivery.delivery.entity.DeliveryEvent;
import com.delivery.delivery.entity.DeliveryStatus;
import com.delivery.delivery.repository.DeliveryEventRepository;
import com.delivery.delivery.repository.DeliveryRepository;
import com.delivery.dispatch.entity.DispatchJob;
import com.delivery.dispatch.repository.DispatchJobRepository;
import com.delivery.dispatch.service.DispatchService;
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
 */
@Service
public class DeliveryRetirementService {
    static final String EVENT_RETIRED = "RETIRED";

    private final DeliveryRepository deliveryRepository;
    private final DispatchJobRepository dispatchJobRepository;
    private final DeliveryEventRepository eventRepository;

    public DeliveryRetirementService(DeliveryRepository deliveryRepository, DispatchJobRepository dispatchJobRepository,
                                     DeliveryEventRepository eventRepository) {
        this.deliveryRepository = deliveryRepository;
        this.dispatchJobRepository = dispatchJobRepository;
        this.eventRepository = eventRepository;
    }

    /** Retires the order's delivery (if any) when it is still before pickup; returns true when something changed. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean retireBeforePickup(UUID tenantId, UUID orderId, String reason) {
        return deliveryRepository.findByTenantIdAndOrderId(tenantId, orderId)
                .map(delivery -> retire(delivery, reason))
                .orElse(false);
    }

    /** Same as above for a delivery row the caller already loaded; idempotent (an already CANCELLED row is a no-op). */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean retireBeforePickup(Delivery delivery, String reason) {
        return retire(delivery, reason);
    }

    private boolean retire(Delivery delivery, String reason) {
        if (!delivery.getStatus().isRetirableBeforePickup()) {
            return false;
        }
        DeliveryStatus previous = delivery.getStatus();
        delivery.updateStatus(DeliveryStatus.CANCELLED);
        for (DispatchJob job : dispatchJobRepository.findByTenantIdAndDeliveryIdAndStatusIn(
                delivery.getTenantId(), delivery.getId(), DispatchService.ACTIVE_JOB_STATUSES)) {
            job.cancel();
        }
        eventRepository.save(new DeliveryEvent(UUID.randomUUID(), delivery.getTenantId(), delivery.getId(), EVENT_RETIRED,
                "Delivery retired from " + previous + ": " + reason));
        return true;
    }
}
