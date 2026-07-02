package com.delivery.dispatch.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.delivery.entity.Delivery;
import com.delivery.delivery.entity.DeliveryEvent;
import com.delivery.delivery.entity.DeliveryStatus;
import com.delivery.delivery.repository.DeliveryEventRepository;
import com.delivery.delivery.repository.DeliveryRepository;
import com.delivery.dispatch.dto.DeliveryEventResponse;
import com.delivery.dispatch.dto.DispatchJobResponse;
import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.entity.DispatchJob;
import com.delivery.dispatch.entity.DispatchJobStatus;
import com.delivery.dispatch.mapper.DispatchMapper;
import com.delivery.dispatch.repository.DispatchJobRepository;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.repository.OrderRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles dispatch assignment, acceptance, rejection, and reassignment. */
@Service
public class DispatchService {
    private final DispatchJobRepository dispatchJobRepository;
    private final CourierService courierService;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryEventRepository deliveryEventRepository;
    private final OrderRepository orderRepository;
    private final DispatchMapper dispatchMapper;
    private final AuditLogService auditLogService;
    private final TenantContext tenantContext;

    public DispatchService(
            DispatchJobRepository dispatchJobRepository,
            CourierService courierService,
            DeliveryRepository deliveryRepository,
            DeliveryEventRepository deliveryEventRepository,
            OrderRepository orderRepository,
            DispatchMapper dispatchMapper,
            AuditLogService auditLogService,
            TenantContext tenantContext) {
        this.dispatchJobRepository = dispatchJobRepository;
        this.courierService = courierService;
        this.deliveryRepository = deliveryRepository;
        this.deliveryEventRepository = deliveryEventRepository;
        this.orderRepository = orderRepository;
        this.dispatchMapper = dispatchMapper;
        this.auditLogService = auditLogService;
        this.tenantContext = tenantContext;
    }

    /** Assigns a ready delivery to the first eligible courier in the tenant. */
    @Transactional
    public DispatchJobResponse assign(UUID orderId, UUID deliveryId, UUID operatingZoneId) {
        UUID tenantId = tenantId();
        Courier courier = courierService.eligibleInZone(operatingZoneId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("no_courier_available", "No verified online courier is available", HttpStatus.CONFLICT));
        DispatchJob job = dispatchJobRepository.save(new DispatchJob(UUID.randomUUID(), tenantId, orderId, deliveryId, courier.getId()));
        Delivery delivery = deliveryRepository.findByTenantIdAndId(tenantId, deliveryId).orElseThrow(() -> new NotFoundException("Delivery was not found"));
        delivery.assignCourier(courier.getId());
        Order order = orderRepository.findByTenantIdAndId(tenantId, orderId).orElseThrow(() -> new NotFoundException("Order was not found"));
        order.markAssignedToCourier();
        return dispatchMapper.toDispatchJobResponse(job);
    }

    /** Lists dispatch jobs visible to the current tenant. */
    @Transactional(readOnly = true)
    public List<DispatchJobResponse> list() {
        return dispatchJobRepository.findAll().stream()
                .filter(job -> job.getTenantId().equals(tenantId()))
                .map(dispatchMapper::toDispatchJobResponse)
                .toList();
    }

    /** Accepts a dispatch job by the assigned courier. */
    @Transactional
    public DispatchJobResponse accept(UUID jobId) {
        DispatchJob job = dispatchJobRepository.findByTenantIdAndId(tenantId(), jobId).orElseThrow(() -> new NotFoundException("Dispatch job was not found"));
        job.accept();
        Delivery delivery = deliveryRepository.findByTenantIdAndId(job.getTenantId(), job.getDeliveryId()).orElseThrow(() -> new NotFoundException("Delivery was not found"));
        delivery.updateStatus(DeliveryStatus.ACCEPTED);
        return dispatchMapper.toDispatchJobResponse(job);
    }

    /** Rejects a dispatch job and marks it reassignable. */
    @Transactional
    public DispatchJobResponse reject(UUID jobId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("rejection_reason_required", "Rejection reason is required", HttpStatus.BAD_REQUEST);
        }
        DispatchJob job = dispatchJobRepository.findByTenantIdAndId(tenantId(), jobId).orElseThrow(() -> new NotFoundException("Dispatch job was not found"));
        job.reject(reason);
        Delivery delivery = deliveryRepository.findByTenantIdAndId(job.getTenantId(), job.getDeliveryId()).orElseThrow(() -> new NotFoundException("Delivery was not found"));
        delivery.updateStatus(DeliveryStatus.REASSIGNED);
        return dispatchMapper.toDispatchJobResponse(job);
    }

    /** Reassigns a previously rejected/reassignable job to another courier. */
    @Transactional
    public DispatchJobResponse reassign(UUID jobId, UUID operatingZoneId) {
        DispatchJob job = dispatchJobRepository.findByTenantIdAndId(tenantId(), jobId).orElseThrow(() -> new NotFoundException("Dispatch job was not found"));
        if (job.getStatus() != DispatchJobStatus.REASSIGNABLE) {
            throw new BusinessException("job_not_reassignable", "Only reassignable jobs can be reassigned", HttpStatus.CONFLICT);
        }
        UUID tenantId = tenantId();
        Courier courier = courierService.eligibleInZone(operatingZoneId)
                .stream()
                .filter(candidate -> !candidate.getId().equals(job.getCourierId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("no_courier_available", "No replacement courier is available", HttpStatus.CONFLICT));
        job.reassign(courier.getId());
        Delivery delivery = deliveryRepository.findByTenantIdAndId(tenantId, job.getDeliveryId()).orElseThrow(() -> new NotFoundException("Delivery was not found"));
        delivery.assignCourier(courier.getId());
        auditLogService.log("DISPATCH_REASSIGN", "dispatch_job", job.getId().toString(), job.getOrderId().toString(), "SUCCESS");
        return dispatchMapper.toDispatchJobResponse(job);
    }

    /** Lists delivery events for operations monitoring and troubleshooting. */
    @Transactional(readOnly = true)
    public java.util.List<DeliveryEventResponse> deliveryEvents(UUID deliveryId) {
        return deliveryEventRepository.findByTenantIdAndDeliveryIdOrderByCreatedAtAsc(tenantId(), deliveryId)
                .stream()
                .map(this::toDeliveryEventResponse)
                .toList();
    }

    private DeliveryEventResponse toDeliveryEventResponse(DeliveryEvent event) {
        return new DeliveryEventResponse(event.getId(), event.getDeliveryId(), event.getEventType(), event.getNotes(), event.getCreatedAt());
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
