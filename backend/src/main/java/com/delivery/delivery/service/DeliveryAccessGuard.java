package com.delivery.delivery.service;

import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.delivery.entity.Delivery;
import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.repository.CourierRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Decides who may act on a delivery: the assigned courier (resolved by profile, not JWT role alone) or OPS/ADMIN. */
@Component
public class DeliveryAccessGuard {
    private final AppUserProfileRepository userProfileRepository;
    private final CourierRepository courierRepository;
    private final TenantContext tenantContext;

    public DeliveryAccessGuard(AppUserProfileRepository userProfileRepository, CourierRepository courierRepository, TenantContext tenantContext) {
        this.userProfileRepository = userProfileRepository;
        this.courierRepository = courierRepository;
        this.tenantContext = tenantContext;
    }

    /** True when the caller holds a tenant-wide operations role (OPS or ADMIN). */
    public boolean isOperator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> "ROLE_OPS".equals(authority) || "ROLE_ADMIN".equals(authority));
    }

    /** Throws 403 unless the caller's courier profile in this tenant is the one assigned to the delivery. */
    public void ensureAssignedCourier(UUID tenantId, Delivery delivery) {
        UUID callerCourierId = currentCourierId(tenantId).orElse(null);
        if (callerCourierId == null || !callerCourierId.equals(delivery.getCourierId())) {
            throw new BusinessException("delivery_access_denied", "Only the assigned courier can perform this action", HttpStatus.FORBIDDEN);
        }
    }

    private Optional<UUID> currentCourierId(UUID tenantId) {
        return tenantContext.currentKeycloakUserId()
                .flatMap(userId -> userProfileRepository.findByTenantIdAndKeycloakUserId(tenantId, userId))
                .flatMap(profile -> courierRepository.findByTenantIdAndUserProfileId(tenantId, profile.getId()))
                .map(Courier::getId);
    }
}
