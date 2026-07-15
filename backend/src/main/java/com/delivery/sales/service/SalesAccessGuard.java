package com.delivery.sales.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.repository.VendorRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Resolves vendor scoping and PII-masking rules for the sales lens (mirrors the catalog guard conventions). */
@Component
public class SalesAccessGuard {
    private static final String VENDOR_ID_CLAIM = "vendor_id";

    private final VendorRepository vendorRepository;

    public SalesAccessGuard(VendorRepository vendorRepository) {
        this.vendorRepository = vendorRepository;
    }

    /** True when the caller must be confined to their own vendor (VENDOR_ADMIN without a tenant-wide role). */
    public boolean isVendorScoped() {
        return hasAuthority("ROLE_VENDOR_ADMIN") && !hasTenantWideRole();
    }

    /** True when customer PII must be masked (SUPPORT without any wider role). */
    public boolean shouldMaskCustomerPii() {
        return hasAuthority("ROLE_SUPPORT")
                && !hasTenantWideRole()
                && !hasAuthority("ROLE_VENDOR_ADMIN");
    }

    /**
     * Resolves the vendor id a vendor-scoped caller is confined to: the optional vendor_id JWT
     * claim first; otherwise the tenant's single bootstrap vendor (merchant registration provisions
     * exactly one vendor per tenant, so the tenant boundary is the vendor boundary).
     * Returns null when the scope cannot be narrowed beyond the tenant.
     */
    public UUID resolveOwnVendorId(UUID tenantId) {
        UUID claim = callerVendorClaim();
        if (claim != null) {
            return claim;
        }
        List<Vendor> vendors = vendorRepository.findByTenantId(tenantId);
        return vendors.size() == 1 ? vendors.get(0).getId() : null;
    }

    /** Throws 403 when a vendor-scoped caller targets an order of another vendor. */
    public void ensureOwnVendorOrder(UUID tenantId, UUID orderVendorId) {
        if (!isVendorScoped()) {
            return;
        }
        UUID ownVendorId = resolveOwnVendorId(tenantId);
        if (ownVendorId != null && !ownVendorId.equals(orderVendorId)) {
            throw new BusinessException("vendor_access_denied", "You can only access sales of your own vendor", HttpStatus.FORBIDDEN);
        }
    }

    private boolean hasTenantWideRole() {
        return hasAuthority("ROLE_ADMIN") || hasAuthority("ROLE_OPS") || hasAuthority("ROLE_FINANCE");
    }

    private boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(authority::equals);
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
