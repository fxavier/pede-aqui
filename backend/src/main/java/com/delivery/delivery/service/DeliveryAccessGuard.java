package com.delivery.delivery.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.delivery.api.AssignedCourierResolver;
import com.delivery.delivery.api.CourierPosition;
import com.delivery.delivery.entity.Delivery;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Decides who may act on a delivery: the assigned courier (resolved by profile, not JWT role alone) or OPS/ADMIN.
 * The caller's courier is resolved by the dispatch module through {@link AssignedCourierResolver} (ADR-0002 C7).
 */
@Component
public class DeliveryAccessGuard {
    private final AssignedCourierResolver courierResolver;

    public DeliveryAccessGuard(AssignedCourierResolver courierResolver) {
        this.courierResolver = courierResolver;
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

    /** True when the caller holds ADMIN (used to record the reporter role of a failed delivery). */
    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_ADMIN"::equals);
    }

    /** The courier's last received position (arrival evidence, LAC-44), resolved by dispatch. */
    public Optional<CourierPosition> lastKnownPosition(UUID tenantId, UUID courierId) {
        return courierResolver.lastKnownPosition(tenantId, courierId);
    }

    /** Throws 403 unless the caller's courier profile in this tenant is the one assigned to the delivery. */
    public void ensureAssignedCourier(UUID tenantId, Delivery delivery) {
        UUID callerCourierId = courierResolver.currentCourierId(tenantId).orElse(null);
        if (callerCourierId == null || !callerCourierId.equals(delivery.getCourierId())) {
            throw new BusinessException("delivery_access_denied", "Only the assigned courier can perform this action", HttpStatus.FORBIDDEN);
        }
    }
}
