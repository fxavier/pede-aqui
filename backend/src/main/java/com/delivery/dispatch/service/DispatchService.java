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
import com.delivery.dispatch.entity.CourierStatus;
import com.delivery.dispatch.entity.DispatchJob;
import com.delivery.dispatch.entity.DispatchJobStatus;
import com.delivery.dispatch.mapper.DispatchMapper;
import com.delivery.dispatch.repository.DispatchJobRepository;
import com.delivery.order.api.OrderStateMachine;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.repository.OrderRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles dispatch assignment, acceptance, rejection, and reassignment. Enforces offer ownership
 * (only the addressed courier may accept/reject), job state guards, offer expiry, and one active
 * job per delivery / one active delivery per courier (backed by the V035 partial unique indexes).
 */
@Service
public class DispatchService {
    /** Maximum page size for job listings. */
    public static final int MAX_PAGE_SIZE = 100;

    /** Job statuses that keep a delivery "taken"; must match the predicate of uq_dispatch_jobs_active_delivery (V035). */
    public static final Set<DispatchJobStatus> ACTIVE_JOB_STATUSES =
            EnumSet.of(DispatchJobStatus.ASSIGNED, DispatchJobStatus.ACCEPTED, DispatchJobStatus.REASSIGNABLE);

    /** Delivery statuses in which the courier is busy; must match the predicate of uq_deliveries_active_courier (V035). */
    static final Set<DeliveryStatus> COURIER_BUSY_DELIVERY_STATUSES = EnumSet.of(
            DeliveryStatus.ASSIGNED, DeliveryStatus.ACCEPTED, DeliveryStatus.ARRIVED_AT_VENDOR,
            DeliveryStatus.PICKED_UP, DeliveryStatus.ON_ROUTE_TO_CUSTOMER, DeliveryStatus.ARRIVED_AT_CUSTOMER);

    private static final String ACTIVE_JOB_INDEX = "uq_dispatch_jobs_active_delivery";
    private static final String ACTIVE_COURIER_INDEX = "uq_deliveries_active_courier";

    private final DispatchJobRepository dispatchJobRepository;
    private final CourierService courierService;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryEventRepository deliveryEventRepository;
    private final OrderRepository orderRepository;
    private final DispatchMapper dispatchMapper;
    private final AuditLogService auditLogService;
    private final TenantContext tenantContext;
    private final Duration offerTtl;
    private final OrderStateMachine stateMachine;

    public DispatchService(
            DispatchJobRepository dispatchJobRepository,
            CourierService courierService,
            DeliveryRepository deliveryRepository,
            DeliveryEventRepository deliveryEventRepository,
            OrderRepository orderRepository,
            DispatchMapper dispatchMapper,
            AuditLogService auditLogService,
            TenantContext tenantContext,
            @Value("${app.dispatch.offer-ttl:60s}") Duration offerTtl,
            OrderStateMachine stateMachine) {
        this.stateMachine = stateMachine;
        this.dispatchJobRepository = dispatchJobRepository;
        this.courierService = courierService;
        this.deliveryRepository = deliveryRepository;
        this.deliveryEventRepository = deliveryEventRepository;
        this.orderRepository = orderRepository;
        this.dispatchMapper = dispatchMapper;
        this.auditLogService = auditLogService;
        this.tenantContext = tenantContext;
        this.offerTtl = offerTtl;
    }

    /**
     * Offers a READY_FOR_PICKUP order's delivery to the first eligible courier of the zone that is not
     * busy with another delivery. The offer expires after app.dispatch.offer-ttl (expiry only blocks
     * acceptance; there is no automatic re-offer yet).
     */
    @Transactional
    public DispatchJobResponse assign(UUID orderId, UUID deliveryId, UUID operatingZoneId) {
        UUID tenantId = tenantId();
        Order order = orderRepository.findByTenantIdAndId(tenantId, orderId).orElseThrow(() -> new NotFoundException("Order was not found"));
        if (order.getStatus() != OrderStatus.READY_FOR_PICKUP) {
            throw new BusinessException("order_not_ready_for_dispatch",
                    "Only orders that are ready for pickup can be dispatched (current status: " + order.getStatus() + ")", HttpStatus.CONFLICT);
        }
        Delivery delivery = deliveryRepository.findByTenantIdAndId(tenantId, deliveryId)
                .filter(found -> found.getOrderId().equals(orderId))
                .orElseThrow(() -> new NotFoundException("Delivery was not found"));
        if (dispatchJobRepository.existsByTenantIdAndDeliveryIdAndStatusIn(tenantId, deliveryId, ACTIVE_JOB_STATUSES)) {
            throw activeJobConflict();
        }
        Courier courier = firstFreeCourier(tenantId, operatingZoneId, null)
                .orElseThrow(() -> new BusinessException("no_courier_available", "No verified online courier is available", HttpStatus.CONFLICT));
        DispatchJob job = new DispatchJob(UUID.randomUUID(), tenantId, orderId, deliveryId, courier.getId());
        job.setExpiresAt(Instant.now().plus(offerTtl));
        job = dispatchJobRepository.save(job);
        delivery.assignCourier(courier.getId());
        stateMachine.transition(order, OrderStatus.ASSIGNED_TO_COURIER, "DISPATCH_ASSIGNED");
        flushTranslatingConflicts();
        return dispatchMapper.toDispatchJobResponse(job);
    }

    /**
     * Lists one page of dispatch jobs of the current tenant, newest first (size capped at MAX_PAGE_SIZE).
     * Callers without a tenant-wide role (OPS/ADMIN) only see the jobs offered to their own courier profile.
     */
    @Transactional(readOnly = true)
    public List<DispatchJobResponse> list(int page, int size) {
        UUID tenantId = tenantId();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        List<DispatchJob> jobs = hasTenantWideRole()
                ? dispatchJobRepository.findByTenantId(tenantId, pageable)
                : dispatchJobRepository.findByTenantIdAndCourierId(tenantId, courierService.requireMine().getId(), pageable);
        return jobs.stream().map(dispatchMapper::toDispatchJobResponse).toList();
    }

    /**
     * Accepts an offer on behalf of the authenticated courier. The transition is a single conditional
     * UPDATE (own job, still ASSIGNED, not expired), so concurrent accepts yield exactly one winner.
     * 404 when the job is not offered to the caller; 409 when it is no longer acceptable.
     */
    @Transactional
    public DispatchJobResponse accept(UUID jobId) {
        UUID tenantId = tenantId();
        Courier courier = courierService.requireMine();
        UUID courierId = courier.getId();
        ownJob(tenantId, jobId, courierId);
        if (courier.getStatus() == CourierStatus.SUSPENDED) {
            throw CourierStatusService.suspended(); // LAC-42: a suspended courier cannot take a pending offer
        }
        Instant now = Instant.now();
        int updated = dispatchJobRepository.acceptOffer(jobId, tenantId, courierId, now, DispatchJobStatus.ASSIGNED, DispatchJobStatus.ACCEPTED);
        DispatchJob job = dispatchJobRepository.findByTenantIdAndId(tenantId, jobId).orElseThrow(() -> new NotFoundException("Dispatch job was not found"));
        if (updated == 0) {
            if (job.getStatus() != DispatchJobStatus.ASSIGNED) {
                throw new BusinessException("dispatch_job_not_assigned",
                        "Only an assigned dispatch job can be accepted (current status: " + job.getStatus() + ")", HttpStatus.CONFLICT);
            }
            if (job.isOfferExpired(now)) {
                throw new BusinessException("dispatch_offer_expired", "The dispatch offer has expired", HttpStatus.CONFLICT);
            }
            throw concurrentChangeConflict();
        }
        Delivery delivery = deliveryRepository.findByTenantIdAndId(tenantId, job.getDeliveryId()).orElseThrow(() -> new NotFoundException("Delivery was not found"));
        delivery.updateStatus(DeliveryStatus.ACCEPTED);
        // LAC-42: BUSY until the delivery ends (CourierStatusService frees it). Re-read: acceptOffer cleared the
        // persistence context (clearAutomatically), so the courier loaded above is detached.
        courierService.requireMine().changeStatus(CourierStatus.BUSY);
        return dispatchMapper.toDispatchJobResponse(job);
    }

    /** Declines an offer on behalf of the authenticated courier and marks the job reassignable (404 if not theirs, 409 if not ASSIGNED). */
    @Transactional
    public DispatchJobResponse reject(UUID jobId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("rejection_reason_required", "Rejection reason is required", HttpStatus.BAD_REQUEST);
        }
        UUID tenantId = tenantId();
        DispatchJob job = ownJob(tenantId, jobId, courierService.requireMine().getId());
        job.reject(reason);
        Delivery delivery = deliveryRepository.findByTenantIdAndId(tenantId, job.getDeliveryId()).orElseThrow(() -> new NotFoundException("Delivery was not found"));
        delivery.updateStatus(DeliveryStatus.REASSIGNED);
        flushTranslatingConflicts();
        return dispatchMapper.toDispatchJobResponse(job);
    }

    /** Reassigns a previously rejected/reassignable job to another free courier with a fresh offer deadline. */
    @Transactional
    public DispatchJobResponse reassign(UUID jobId, UUID operatingZoneId) {
        UUID tenantId = tenantId();
        DispatchJob job = dispatchJobRepository.findByTenantIdAndId(tenantId, jobId).orElseThrow(() -> new NotFoundException("Dispatch job was not found"));
        if (job.getStatus() != DispatchJobStatus.REASSIGNABLE) {
            throw new BusinessException("job_not_reassignable", "Only reassignable jobs can be reassigned", HttpStatus.CONFLICT);
        }
        Courier courier = firstFreeCourier(tenantId, operatingZoneId, job.getCourierId())
                .orElseThrow(() -> new BusinessException("no_courier_available", "No replacement courier is available", HttpStatus.CONFLICT));
        job.reassign(courier.getId());
        job.setExpiresAt(Instant.now().plus(offerTtl));
        Delivery delivery = deliveryRepository.findByTenantIdAndId(tenantId, job.getDeliveryId()).orElseThrow(() -> new NotFoundException("Delivery was not found"));
        delivery.assignCourier(courier.getId());
        flushTranslatingConflicts();
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

    /** Loads a job of the tenant that is offered to the given courier; anything else is reported as not found. */
    private DispatchJob ownJob(UUID tenantId, UUID jobId, UUID courierId) {
        return dispatchJobRepository.findByTenantIdAndId(tenantId, jobId)
                .filter(job -> courierId.equals(job.getCourierId()))
                .orElseThrow(() -> new NotFoundException("Dispatch job was not found"));
    }

    /** First eligible courier of the zone without an active delivery, optionally excluding one courier. */
    private Optional<Courier> firstFreeCourier(UUID tenantId, UUID operatingZoneId, UUID excludedCourierId) {
        return courierService.eligibleInZone(operatingZoneId).stream()
                .filter(candidate -> !candidate.getId().equals(excludedCourierId))
                .filter(candidate -> !deliveryRepository.existsByTenantIdAndCourierIdAndStatusIn(
                        tenantId, candidate.getId(), COURIER_BUSY_DELIVERY_STATUSES))
                .findFirst();
    }

    /**
     * Flushes pending dispatch writes inside the transaction so that the V035 exclusivity indexes and
     * optimistic locks fail here, and maps exactly those failures to 409. Any other integrity error propagates.
     */
    private void flushTranslatingConflicts() {
        try {
            dispatchJobRepository.flush();
        } catch (DataIntegrityViolationException e) {
            String detail = String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage());
            if (detail.contains(ACTIVE_JOB_INDEX)) {
                throw activeJobConflict();
            }
            if (detail.contains(ACTIVE_COURIER_INDEX)) {
                throw new BusinessException("courier_busy", "The selected courier already has an active delivery; retry the assignment", HttpStatus.CONFLICT);
            }
            throw e;
        } catch (OptimisticLockingFailureException e) {
            throw concurrentChangeConflict();
        }
    }

    private BusinessException activeJobConflict() {
        return new BusinessException("dispatch_job_already_active", "The delivery already has an active dispatch job", HttpStatus.CONFLICT);
    }

    private BusinessException concurrentChangeConflict() {
        return new BusinessException("dispatch_job_conflict", "The dispatch job changed concurrently; reload and retry", HttpStatus.CONFLICT);
    }

    private boolean hasTenantWideRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> "ROLE_OPS".equals(authority) || "ROLE_ADMIN".equals(authority));
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
