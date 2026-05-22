package com.delivery.dispatch.service;

import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.dispatch.dto.CourierResponse;
import com.delivery.dispatch.dto.CourierEarningsSummaryResponse;
import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.entity.CourierVerificationStatus;
import com.delivery.dispatch.mapper.DispatchMapper;
import com.delivery.dispatch.repository.CourierRepository;
import com.delivery.delivery.entity.DeliveryStatus;
import com.delivery.delivery.repository.DeliveryRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles courier profile and availability state for assignment eligibility. */
@Service
public class CourierService {
    private final CourierRepository courierRepository;
    private final AppUserProfileRepository userProfileRepository;
    private final DeliveryRepository deliveryRepository;
    private final DispatchMapper dispatchMapper;
    private final TenantContext tenantContext;

    public CourierService(CourierRepository courierRepository, AppUserProfileRepository userProfileRepository, DeliveryRepository deliveryRepository, DispatchMapper dispatchMapper, TenantContext tenantContext) {
        this.courierRepository = courierRepository;
        this.userProfileRepository = userProfileRepository;
        this.deliveryRepository = deliveryRepository;
        this.dispatchMapper = dispatchMapper;
        this.tenantContext = tenantContext;
    }

    /** Creates a courier profile for the authenticated user if one does not exist. */
    @Transactional
    public CourierResponse ensureMine() {
        UUID tenantId = tenantId();
        String userId = tenantContext.currentKeycloakUserId().orElseThrow(() -> new BusinessException("user_required", "Authenticated user is required", HttpStatus.FORBIDDEN));
        UUID profileId = userProfileRepository.findByTenantIdAndKeycloakUserId(tenantId, userId)
                .orElseThrow(() -> new NotFoundException("User profile was not found"))
                .getId();
        Courier courier = courierRepository.findByTenantIdAndUserProfileId(tenantId, profileId)
                .orElseGet(() -> courierRepository.save(new Courier(UUID.randomUUID(), tenantId, profileId, null)));
        if (courier.getVerificationStatus() == CourierVerificationStatus.PENDING) {
            courier.approve();
        }
        return dispatchMapper.toCourierResponse(courier);
    }

    /** Updates the availability of the authenticated courier. */
    @Transactional
    public CourierResponse setMyAvailability(boolean available) {
        Courier courier = requireMine();
        if (courier.getVerificationStatus() != CourierVerificationStatus.APPROVED) {
            throw new BusinessException("courier_not_verified", "Courier must be verified before going online", HttpStatus.CONFLICT);
        }
        courier.setAvailable(available);
        return dispatchMapper.toCourierResponse(courier);
    }

    /** Lists couriers eligible for assignment in a given operating zone. */
    @Transactional(readOnly = true)
    public List<Courier> eligibleInZone(UUID operatingZoneId) {
        return courierRepository.findByTenantIdAndVerificationStatusAndAvailableAndOperatingZoneId(
                tenantId(),
                CourierVerificationStatus.APPROVED,
                true,
                operatingZoneId);
    }

    /** Returns courier delivery outcome counters and a simple earnings summary. */
    @Transactional(readOnly = true)
    public CourierEarningsSummaryResponse myEarningsSummary() {
        Courier courier = requireMine();
        int completed = deliveryRepository.findByTenantIdAndCourierIdAndStatus(tenantId(), courier.getId(), DeliveryStatus.DELIVERED).size();
        int failed = deliveryRepository.findByTenantIdAndCourierIdAndStatus(tenantId(), courier.getId(), DeliveryStatus.FAILED_DELIVERY).size();
        BigDecimal earnings = BigDecimal.valueOf(completed).multiply(new BigDecimal("150.00"));
        return new CourierEarningsSummaryResponse(completed, failed, earnings);
    }

    /** Returns the tenant-scoped courier record of the authenticated user. */
    @Transactional(readOnly = true)
    public Courier requireMine() {
        UUID tenantId = tenantId();
        String userId = tenantContext.currentKeycloakUserId().orElseThrow(() -> new BusinessException("user_required", "Authenticated user is required", HttpStatus.FORBIDDEN));
        UUID profileId = userProfileRepository.findByTenantIdAndKeycloakUserId(tenantId, userId)
                .orElseThrow(() -> new NotFoundException("User profile was not found"))
                .getId();
        return courierRepository.findByTenantIdAndUserProfileId(tenantId, profileId).orElseThrow(() -> new NotFoundException("Courier profile was not found"));
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
