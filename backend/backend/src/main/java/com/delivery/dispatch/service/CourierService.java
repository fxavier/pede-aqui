package com.delivery.dispatch.service;

import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import com.delivery.dispatch.dto.CourierResponse;
import com.delivery.dispatch.dto.CourierEarningsSummaryResponse;
import com.delivery.dispatch.dto.CreateCourierRequest;
import com.delivery.dispatch.dto.CreateCourierDocumentRequest;
import com.delivery.dispatch.dto.CourierDocumentResponse;
import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.entity.CourierDocument;
import com.delivery.dispatch.entity.CourierVerificationStatus;
import com.delivery.dispatch.mapper.DispatchMapper;
import com.delivery.dispatch.repository.CourierRepository;
import com.delivery.dispatch.repository.CourierDocumentRepository;
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
    private final CourierDocumentRepository courierDocumentRepository;
    private final AppUserProfileRepository userProfileRepository;
    private final DeliveryRepository deliveryRepository;
    private final DispatchMapper dispatchMapper;
    private final TenantContext tenantContext;

    public CourierService(CourierRepository courierRepository, CourierDocumentRepository courierDocumentRepository, AppUserProfileRepository userProfileRepository, DeliveryRepository deliveryRepository, DispatchMapper dispatchMapper, TenantContext tenantContext) {
        this.courierRepository = courierRepository;
        this.courierDocumentRepository = courierDocumentRepository;
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

    @Transactional
    public CourierResponse create(CreateCourierRequest request) {
        UUID tenantId = tenantId();
        
        // Check if user is admin/operations or enforce ownership for courier self-registration
        if (!isAdminOrOperations()) {
            // Resolve caller's keycloak user ID
            String callerKeycloakUserId = tenantContext.currentKeycloakUserId()
                    .orElseThrow(() -> new BusinessException("user_required", "Authenticated user is required", HttpStatus.FORBIDDEN));
            
            // Look up caller's profile
            var callerProfile = userProfileRepository.findByTenantIdAndKeycloakUserId(tenantId, callerKeycloakUserId)
                    .orElseThrow(() -> new NotFoundException("User profile was not found"));
            
            // Verify ownership: caller can only register their own profile
            if (!callerProfile.getId().equals(request.userProfileId())) {
                throw new BusinessException("forbidden_user_profile", "Couriers may only register their own profile", HttpStatus.FORBIDDEN);
            }
        }
        
        Courier courier = new Courier(UUID.randomUUID(), tenantId, request.userProfileId(), request.operatingZoneId());
        courier.updateProfile(request.fullName(), request.phone(), request.nif(), request.vehicleType(), request.vehiclePlate(), request.dateOfBirth());
        courierRepository.save(courier);
        return dispatchMapper.toCourierResponse(courier);
    }

    @Transactional(readOnly = true)
    public List<CourierResponse> listAll() {
        if (tenantContext.isPlatformAdmin()) {
            return courierRepository.findAll().stream().map(dispatchMapper::toCourierResponse).toList();
        }
        return courierRepository.findByTenantId(tenantId()).stream().map(dispatchMapper::toCourierResponse).toList();
    }

    @Transactional(readOnly = true)
    public CourierResponse getById(UUID courierId) {
        UUID tenantId = tenantId();
        Courier courier = courierRepository.findByTenantIdAndId(tenantId, courierId)
                .orElseThrow(() -> new NotFoundException("Courier not found"));
        return dispatchMapper.toCourierResponse(courier);
    }

    @Transactional
    public CourierDocumentResponse addDocument(UUID courierId, CreateCourierDocumentRequest request) {
        UUID tenantId = tenantId();
        Courier courier = courierRepository.findByTenantIdAndId(tenantId, courierId)
                .orElseThrow(() -> new NotFoundException("Courier not found"));
        
        CourierDocument document = new CourierDocument(
            UUID.randomUUID(),
            tenantId,
            courier.getId(),
            request.documentType(),
            request.storageKey()
        );
        courierDocumentRepository.save(document);
        return dispatchMapper.toCourierDocumentResponse(document);
    }

    @Transactional(readOnly = true)
    public List<CourierDocumentResponse> listDocuments(UUID courierId) {
        UUID tenantId = tenantId();
        return courierDocumentRepository.findAllByCourierIdAndTenantId(courierId, tenantId)
                .stream()
                .map(dispatchMapper::toCourierDocumentResponse)
                .toList();
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }

    private boolean isAdminOrOperations() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority) || "ROLE_OPERATIONS".equals(authority));
    }
}
