package com.delivery.sales.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.vendor.api.MerchantScopeGuard;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Sales-lens access rules: PII masking plus the merchant vendor scope, which is delegated to the shared
 * {@link MerchantScopeGuard} (ADR-0001 step 3 / ADR-0002 C5). Also reused by order fulfilment and order listing
 * (INC-04). Every scope method fails closed (403 {@code vendor_scope_unresolved}) when the vendor cannot be resolved.
 */
@Component
public class SalesAccessGuard {
    private final MerchantScopeGuard merchantScopeGuard;

    public SalesAccessGuard(MerchantScopeGuard merchantScopeGuard) {
        this.merchantScopeGuard = merchantScopeGuard;
    }

    /** True when the caller must be confined to their own vendor (VENDOR_ADMIN without a tenant-wide role). */
    public boolean isVendorScoped() {
        return merchantScopeGuard.isVendorAdminScoped();
    }

    /** True when the caller is a merchant user (VENDOR_ADMIN or VENDOR_STAFF) without a tenant-wide role. */
    public boolean isMerchantScoped() {
        return merchantScopeGuard.isMerchantScoped();
    }

    /** Vendor the merchant caller is confined to; 403 {@code vendor_scope_unresolved} when it cannot be determined. */
    public UUID requireOwnVendorId(UUID tenantId) {
        return merchantScopeGuard.requireOwnVendorId(tenantId);
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
                && !merchantScopeGuard.hasTenantWideRole()
                && !hasAuthority("ROLE_VENDOR_ADMIN");
    }

    /**
     * Resolves the vendor a vendor-scoped caller is confined to. Since ADR-0001 step 3 this never returns null:
     * an unresolvable scope is a 403 {@code vendor_scope_unresolved} instead of a tenant-wide result.
     */
    public UUID resolveOwnVendorId(UUID tenantId) {
        return merchantScopeGuard.requireOwnVendorId(tenantId);
    }

    /** Throws 403 when a vendor-scoped caller targets an order of another vendor (or has no resolvable vendor). */
    public void ensureOwnVendorOrder(UUID tenantId, UUID orderVendorId) {
        if (!isVendorScoped()) {
            return;
        }
        UUID ownVendorId = merchantScopeGuard.requireOwnVendorId(tenantId);
        if (!ownVendorId.equals(orderVendorId)) {
            throw new BusinessException(MerchantScopeGuard.ACCESS_DENIED_CODE, "You can only access sales of your own vendor", HttpStatus.FORBIDDEN);
        }
    }

    private boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(authority::equals);
    }
}
