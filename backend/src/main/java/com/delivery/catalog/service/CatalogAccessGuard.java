package com.delivery.catalog.service;

import com.delivery.vendor.api.MerchantScopeGuard;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Enforces own-vendor scoping for merchant catalog/stock mutations; OPS/ADMIN are tenant-wide. The caller's vendor
 * is resolved by the shared {@link MerchantScopeGuard} (merchant_users first — ADR-0001 step 3 / ADR-0002 C6).
 */
@Component
public class CatalogAccessGuard {
    private final MerchantScopeGuard merchantScopeGuard;

    public CatalogAccessGuard(MerchantScopeGuard merchantScopeGuard) {
        this.merchantScopeGuard = merchantScopeGuard;
    }

    /** Throws 403 when a merchant user targets a vendor other than their own (or has no resolvable vendor). */
    public void ensureCanManageVendor(UUID targetVendorId) {
        if (hasTenantWideRole()) {
            return;
        }
        merchantScopeGuard.ensureCallerOwnsVendor(targetVendorId, "You can only manage products of your own vendor");
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
}
