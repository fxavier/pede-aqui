package com.delivery.dispatch.dto;

import com.delivery.dispatch.entity.DispatchJobStatus;
import java.util.UUID;

/** Dispatch job data returned to courier and operations APIs. */
public record DispatchJobResponse(UUID id, UUID orderId, UUID deliveryId, UUID courierId, DispatchJobStatus status, String rejectionReason) {}
