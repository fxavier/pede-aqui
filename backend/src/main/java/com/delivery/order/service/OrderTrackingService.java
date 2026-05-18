package com.delivery.order.service;

import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.order.dto.TrackingResponse;
import com.delivery.order.mapper.OrderMapper;
import com.delivery.order.repository.OrderRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Provides customer-safe order tracking details. */
@Service
public class OrderTrackingService {
    private final OrderRepository orderRepository;
    private final AppUserProfileRepository userProfileRepository;
    private final OrderMapper mapper;
    private final TenantContext tenantContext;

    public OrderTrackingService(OrderRepository orderRepository, AppUserProfileRepository userProfileRepository, OrderMapper mapper, TenantContext tenantContext) {
        this.orderRepository = orderRepository;
        this.userProfileRepository = userProfileRepository;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
    }

    /** Returns current status and the customer delivery code for a tenant-scoped order. */
    @Transactional(readOnly = true)
    public TrackingResponse track(UUID orderId) {
        UUID tenantId = tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
        String keycloakUserId = tenantContext.currentKeycloakUserId().orElseThrow(() -> new BusinessException("user_required", "Authenticated user is required", HttpStatus.FORBIDDEN));

        AppUserProfile currentUser = userProfileRepository
                .findByTenantIdAndKeycloakUserId(tenantId, keycloakUserId)
                .orElseThrow(() -> new NotFoundException("User profile was not found"));

        return orderRepository.findByTenantIdAndId(tenantId, orderId)
                .filter(order -> order.getCustomerId().equals(currentUser.getId()))
                .map(mapper::toTrackingResponse)
                .orElseThrow(() -> new NotFoundException("Order was not found"));
    }
}
