package com.delivery.sales.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.order.api.CustomerPiiMasking;
import com.delivery.vendor.api.MerchantScopeGuard;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Sales-lens access rules: PII masking plus the merchant vendor scope, which is delegated to the shared
 * {@link MerchantScopeGuard} (ADR-0001 step 3 / ADR-0002 C5); the PII rule is the order module's
 * {@link CustomerPiiMasking} (order listings use it directly, so order no longer depends on sales). Every scope method
 * fails closed (403 {@code vendor_scope_unresolved}) when the vendor cannot be resolved.
 */
@Component
public class SalesAccessGuard {
    private final MerchantScopeGuard merchantScopeGuard;
    private final CustomerPiiMasking piiMasking;

    public SalesAccessGuard(MerchantScopeGuard merchantScopeGuard) {
        this.merchantScopeGuard = merchantScopeGuard;
        this.piiMasking = new CustomerPiiMasking(merchantScopeGuard);
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
        return CustomerPiiMasking.maskName(name);
    }

    /** True when customer PII must be masked (SUPPORT without any wider role). */
    public boolean shouldMaskCustomerPii() {
        return piiMasking.shouldMaskCustomerPii();
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

}
