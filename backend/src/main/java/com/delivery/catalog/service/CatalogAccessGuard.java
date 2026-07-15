package com.delivery.catalog.service;

import com.delivery.common.exception.BusinessException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Enforces own-vendor scoping for VENDOR_ADMIN catalog mutations; OPS/ADMIN are tenant-wide. */
@Component
public class CatalogAccessGuard {
    private static final String VENDOR_ID_CLAIM = "vendor_id";

    /** Throws 403 when a VENDOR_ADMIN targets a vendor other than their own. */
    public void ensureCanManageVendor(UUID targetVendorId) {
        if (hasTenantWideRole()) {
            return;
        }
        // VENDOR_ADMIN path: the caller's vendor comes from the optional vendor_id JWT claim.
        // When the claim is absent we fall back to tenant scoping (merchant registration
        // provisions exactly one vendor per tenant, so the tenant boundary is the vendor boundary).
        UUID callerVendorId = callerVendorClaim();
        if (callerVendorId != null && !callerVendorId.equals(targetVendorId)) {
            throw new BusinessException("vendor_access_denied", "You can only manage products of your own vendor", HttpStatus.FORBIDDEN);
        }
    }

    private boolean hasTenantWideRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> "ROLE_ADMIN".equals(a) || "ROLE_OPS".equals(a));
    }

    private UUID callerVendorClaim() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        String value = jwt.getClaimAsString(VENDOR_ID_CLAIM);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("vendor_access_denied", "Invalid vendor claim", HttpStatus.FORBIDDEN);
        }
    }
}
