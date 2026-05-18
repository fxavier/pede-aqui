package com.delivery.dispatch.mapper;

import com.delivery.dispatch.dto.CourierResponse;
import com.delivery.dispatch.dto.DispatchJobResponse;
import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.entity.DispatchJob;
import org.springframework.stereotype.Component;

/** Maps dispatch and courier entities to API response DTOs. */
@Component
public class DispatchMapper {
    public CourierResponse toCourierResponse(Courier courier) {
        return new CourierResponse(courier.getId(), courier.getUserProfileId(), courier.getVerificationStatus(), courier.isAvailable(), courier.getOperatingZoneId());
    }

    public DispatchJobResponse toDispatchJobResponse(DispatchJob job) {
        return new DispatchJobResponse(job.getId(), job.getOrderId(), job.getDeliveryId(), job.getCourierId(), job.getStatus(), job.getRejectionReason());
    }
}
