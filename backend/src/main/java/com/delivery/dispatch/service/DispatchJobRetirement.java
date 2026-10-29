package com.delivery.dispatch.service;

import com.delivery.delivery.api.DeliveryRetirementListener;
import com.delivery.dispatch.entity.DispatchJob;
import com.delivery.dispatch.repository.DispatchJobRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Dispatch side of a delivery retirement: cancels the delivery's active dispatch job(s), freeing the courier. Runs in
 * the retiring transaction (called by delivery's DeliveryRetirementService through the delivery API, ADR-0002 C7).
 */
@Component
public class DispatchJobRetirement implements DeliveryRetirementListener {
    private final DispatchJobRepository dispatchJobRepository;

    public DispatchJobRetirement(DispatchJobRepository dispatchJobRepository) {
        this.dispatchJobRepository = dispatchJobRepository;
    }

    /** Cancels every active (ASSIGNED/ACCEPTED/REASSIGNABLE) job of the delivery; already-final jobs are untouched. */
    @Override
    public void onDeliveryRetired(UUID tenantId, UUID deliveryId) {
        for (DispatchJob job : dispatchJobRepository.findByTenantIdAndDeliveryIdAndStatusIn(
                tenantId, deliveryId, DispatchService.ACTIVE_JOB_STATUSES)) {
            job.cancel();
        }
    }
}
