package com.delivery.dispatch.service;

import com.delivery.delivery.api.DeliveryCourierListener;
import com.delivery.delivery.api.DeliveryRetirementListener;
import com.delivery.dispatch.entity.DispatchJob;
import com.delivery.dispatch.repository.DispatchJobRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Dispatch side of a delivery retirement: cancels the delivery's active dispatch job(s), freeing the courier (and,
 * LAC-42, moving a BUSY courier back to AVAILABLE). Runs in the retiring transaction (called by delivery's
 * DeliveryRetirementService through the delivery API, ADR-0002 C7).
 */
@Component
public class DispatchJobRetirement implements DeliveryRetirementListener {
    private final DispatchJobRepository dispatchJobRepository;
    private DeliveryCourierListener courierListener = DeliveryCourierListener.NOOP;

    public DispatchJobRetirement(DispatchJobRepository dispatchJobRepository) {
        this.dispatchJobRepository = dispatchJobRepository;
    }

    /** LAC-42: the courier status side ({@link CourierStatusService}); optional so slice tests need no extra wiring. */
    @Autowired(required = false)
    public void setCourierListener(DeliveryCourierListener courierListener) { this.courierListener = courierListener; }

    /** Cancels every active (ASSIGNED/ACCEPTED/REASSIGNABLE) job of the delivery; already-final jobs are untouched. */
    @Override
    public void onDeliveryRetired(UUID tenantId, UUID deliveryId) {
        for (DispatchJob job : dispatchJobRepository.findByTenantIdAndDeliveryIdAndStatusIn(
                tenantId, deliveryId, DispatchService.ACTIVE_JOB_STATUSES)) {
            job.cancel();
            if (job.getCourierId() != null) {
                courierListener.onCourierFreed(tenantId, job.getCourierId());
            }
        }
    }
}
