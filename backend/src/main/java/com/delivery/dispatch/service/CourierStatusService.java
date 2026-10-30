package com.delivery.dispatch.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditActions;
import com.delivery.common.service.AuditLogService;
import com.delivery.delivery.api.DeliveryCourierListener;
import com.delivery.delivery.entity.DeliveryStatus;
import com.delivery.delivery.repository.DeliveryRepository;
import com.delivery.dispatch.dto.CourierResponse;
import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.entity.CourierStatus;
import com.delivery.dispatch.entity.CourierVerificationStatus;
import com.delivery.dispatch.mapper.DispatchMapper;
import com.delivery.dispatch.repository.CourierLocationRepository;
import com.delivery.dispatch.repository.CourierRepository;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Courier operational status (LAC-42): the courier goes online/offline, OPS suspends/reinstates (audited), and the
 * delivery lifecycle frees a BUSY courier when its delivery ends ({@link DeliveryCourierListener}). BUSY itself is set
 * by {@link DispatchService#accept}. Going off duty also drops the courier's latest position (DB row + Redis), since
 * location is only kept while on duty (LAC-40).
 */
@Service
public class CourierStatusService implements DeliveryCourierListener {
    /**
     * Delivery statuses in which the courier is working an accepted delivery (BUSY). Unlike
     * {@code DispatchService.COURIER_BUSY_DELIVERY_STATUSES} it excludes ASSIGNED: a pending offer leaves the courier
     * AVAILABLE until they accept it.
     */
    public static final Set<DeliveryStatus> ENGAGED_DELIVERY_STATUSES = EnumSet.of(
            DeliveryStatus.ACCEPTED, DeliveryStatus.ARRIVED_AT_VENDOR, DeliveryStatus.PICKED_UP,
            DeliveryStatus.ON_ROUTE_TO_CUSTOMER, DeliveryStatus.ARRIVED_AT_CUSTOMER);

    private final CourierRepository courierRepository;
    private final CourierService courierService;
    private final DeliveryRepository deliveryRepository;
    private final CourierLocationRepository locationRepository;
    private final CourierPresenceStore presenceStore;
    private final AuditLogService auditLogService;
    private final TenantContext tenantContext;
    private final DispatchMapper mapper;

    public CourierStatusService(CourierRepository courierRepository, CourierService courierService, DeliveryRepository deliveryRepository,
                                CourierLocationRepository locationRepository, CourierPresenceStore presenceStore,
                                AuditLogService auditLogService, TenantContext tenantContext, DispatchMapper mapper) {
        this.courierRepository = courierRepository;
        this.courierService = courierService;
        this.deliveryRepository = deliveryRepository;
        this.locationRepository = locationRepository;
        this.presenceStore = presenceStore;
        this.auditLogService = auditLogService;
        this.tenantContext = tenantContext;
        this.mapper = mapper;
    }

    /**
     * The authenticated courier goes online (AVAILABLE, or BUSY when they still work an accepted delivery) or offline.
     * 400 {@code invalid_courier_status} for BUSY/SUSPENDED, 409 {@code courier_suspended}, 409
     * {@code courier_not_verified}, 409 {@code courier_has_active_delivery} when going offline mid-delivery.
     */
    @Transactional
    public CourierResponse setMyStatus(CourierStatus requested) {
        if (requested != CourierStatus.AVAILABLE && requested != CourierStatus.OFFLINE) {
            throw new BusinessException("invalid_courier_status", "A courier can only set AVAILABLE or OFFLINE", HttpStatus.BAD_REQUEST);
        }
        Courier courier = courierService.requireMine();
        if (courier.getStatus() == CourierStatus.SUSPENDED) {
            throw suspended();
        }
        if (courier.getVerificationStatus() != CourierVerificationStatus.APPROVED) {
            throw new BusinessException("courier_not_verified", "Courier must be verified before going online", HttpStatus.CONFLICT);
        }
        boolean engaged = hasEngagedDelivery(courier);
        if (requested == CourierStatus.AVAILABLE) {
            courier.changeStatus(engaged ? CourierStatus.BUSY : CourierStatus.AVAILABLE);
        } else {
            if (engaged) {
                throw new BusinessException("courier_has_active_delivery",
                        "Finish or hand over the active delivery before going offline", HttpStatus.CONFLICT);
            }
            goOffDuty(courier, CourierStatus.OFFLINE);
        }
        return mapper.toCourierResponse(courier);
    }

    /** OPS/ADMIN: suspends a courier of the tenant (idempotent), drops their presence and audits it. */
    @Transactional
    public CourierResponse suspend(UUID courierId, String reason) {
        Courier courier = tenantCourier(courierId);
        if (courier.getStatus() != CourierStatus.SUSPENDED) {
            CourierStatus previous = courier.getStatus();
            courier.suspend(reason.strip());
            clearPresence(courier);
            auditLogService.log(AuditActions.COURIER_SUSPENDED, "courier", courierId.toString(), "from=" + previous, "SUCCESS");
        }
        return mapper.toCourierResponse(courier);
    }

    /**
     * OPS/ADMIN: lifts a suspension. The courier becomes OFFLINE (BUSY if they still hold an accepted delivery) and
     * must go online again; 409 {@code courier_not_suspended} otherwise. Audited.
     */
    @Transactional
    public CourierResponse reinstate(UUID courierId) {
        Courier courier = tenantCourier(courierId);
        if (courier.getStatus() != CourierStatus.SUSPENDED) {
            throw new BusinessException("courier_not_suspended", "The courier is not suspended", HttpStatus.CONFLICT);
        }
        courier.changeStatus(hasEngagedDelivery(courier) ? CourierStatus.BUSY : CourierStatus.OFFLINE);
        auditLogService.log(AuditActions.COURIER_REINSTATED, "courier", courierId.toString(), "to=" + courier.getStatus(), "SUCCESS");
        return mapper.toCourierResponse(courier);
    }

    /** Delivery ended (DELIVERED / FAILED_DELIVERY / retired): a BUSY courier with no other accepted delivery becomes AVAILABLE. */
    @Override
    @Transactional
    public void onCourierFreed(UUID tenantId, UUID courierId) {
        courierRepository.findByTenantIdAndId(tenantId, courierId).ifPresent(this::releaseIfIdle);
    }

    /** BUSY -> AVAILABLE when the courier no longer works an accepted delivery; returns true when it changed. */
    public boolean releaseIfIdle(Courier courier) {
        if (courier.getStatus() != CourierStatus.BUSY || hasEngagedDelivery(courier)) {
            return false;
        }
        courier.changeStatus(CourierStatus.AVAILABLE);
        return true;
    }

    /** Moves the courier off duty (OFFLINE or SUSPENDED handled by callers) and drops their latest position. */
    public void goOffDuty(Courier courier, CourierStatus next) {
        courier.changeStatus(next);
        clearPresence(courier);
    }

    /** Deletes the latest position (DB) and the Redis presence/GEO entries of the courier. */
    public void clearPresence(Courier courier) {
        locationRepository.deleteLast(courier.getId());
        presenceStore.remove(courier.getTenantId(), courier.getId());
    }

    /** True when the courier works an accepted delivery (ACCEPTED .. ARRIVED_AT_CUSTOMER). */
    public boolean hasEngagedDelivery(Courier courier) {
        return deliveryRepository.existsByTenantIdAndCourierIdAndStatusIn(courier.getTenantId(), courier.getId(), ENGAGED_DELIVERY_STATUSES);
    }

    public static BusinessException suspended() {
        return new BusinessException("courier_suspended", "The courier is suspended", HttpStatus.CONFLICT);
    }

    private Courier tenantCourier(UUID courierId) {
        UUID tenantId = tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
        return courierRepository.findByTenantIdAndId(tenantId, courierId).orElseThrow(() -> new NotFoundException("Courier not found"));
    }
}
