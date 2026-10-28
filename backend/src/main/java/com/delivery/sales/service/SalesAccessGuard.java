package com.delivery.sales.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.repository.VendorRepository;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Resolves vendor scoping and PII-masking rules for the sales lens (mirrors the catalog guard conventions).
 * Also reused by order fulfilment and order listing (INC-04) via the merchant-scoped, fail-closed methods.
 */
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

    /**
     * True when the caller is a merchant user (VENDOR_ADMIN or VENDOR_STAFF) without a tenant-wide role.
     * VENDOR_STAFF has no user-to-vendor association yet, so it is resolved exactly like VENDOR_ADMIN.
     */
    public boolean isMerchantScoped() {
        return (hasAuthority("ROLE_VENDOR_ADMIN") || hasAuthority("ROLE_VENDOR_STAFF")) && !hasTenantWideRole();
    }

    /**
     * Fail-closed variant of {@link #resolveOwnVendorId}: throws 403 when a merchant caller's vendor
     * cannot be determined (no vendor_id claim and the tenant has zero or several vendors).
     */
    public UUID requireOwnVendorId(UUID tenantId) {
        UUID ownVendorId = resolveOwnVendorId(tenantId);
        if (ownVendorId == null) {
            throw new BusinessException("vendor_scope_unresolved", "Unable to resolve the vendor for the current merchant user", HttpStatus.FORBIDDEN);
        }
        return ownVendorId;
    }

    /** Masks a person's name: keeps the first letter of each word ("Ana Lima" -> "A*** L***"). */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        return Arrays.stream(name.trim().split("\\s+"))
                .map(word -> word.charAt(0) + "***")
                .collect(Collectors.joining(" "));
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
