package com.delivery.privacy.service;

import com.delivery.auth.api.UserAccounts;
import com.delivery.auth.api.UserAccounts.SelfProfile;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Resolves the data subject of a self-service request (W7-C): the caller's own profile in the resolved tenant (for a
 * customer, the tenant mapped from the OIDC client). No tenant → 403 {@code tenant_required}; no profile (or a
 * pseudonymised one) → 404 {@code not_found}. Never accepts a profile id from the request.
 */
@Component
public class DataSubjectResolver {
    private final TenantContext tenantContext;
    private final UserAccounts userAccounts;

    public DataSubjectResolver(TenantContext tenantContext, UserAccounts userAccounts) {
        this.tenantContext = tenantContext;
        this.userAccounts = userAccounts;
    }

    /** The caller's own, non-deleted profile. */
    public SelfProfile currentSubject() {
        var tenantId = tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context required", HttpStatus.FORBIDDEN));
        String keycloakUserId = tenantContext.currentKeycloakUserId()
                .orElseThrow(() -> new NotFoundException("User profile was not found"));
        return userAccounts.findSelf(tenantId, keycloakUserId)
                .filter(profile -> !UserAccounts.STATUS_DELETED.equals(profile.status()))
                .orElseThrow(() -> new NotFoundException("User profile was not found"));
    }

    /** True when the caller is a customer-only principal (staff/courier accounts are closed by an administrator). */
    public boolean isCustomerPrincipal() {
        return tenantContext.isCustomerPrincipal();
    }
}
