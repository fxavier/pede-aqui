package com.delivery.vendor;

import static org.mockito.Mockito.mock;

import com.delivery.common.security.TenantContext;
import com.delivery.vendor.api.MerchantScopeGuard;
import com.delivery.vendor.config.MerchantScopeProperties;
import com.delivery.vendor.repository.MerchantUserRepository;
import com.delivery.vendor.repository.VendorRepository;

/**
 * Test helpers building a real {@link MerchantScopeGuard} for unit tests of services that delegate to it.
 * {@link #legacyFallback} has no merchant_users rows (mock repository) and the single-vendor fallback on, so
 * pre-ADR-0001 unit tests keep their vendor_id-claim / single-vendor fixtures.
 */
public final class MerchantScopeFixtures {
    private MerchantScopeFixtures() {
    }

    /** Guard with an empty merchant_users and the legacy fallback enabled, reading vendors from the given repository. */
    public static MerchantScopeGuard legacyFallback(TenantContext tenantContext, VendorRepository vendorRepository) {
        return new MerchantScopeGuard(tenantContext, mock(MerchantUserRepository.class), vendorRepository, new MerchantScopeProperties());
    }

    /** Same as {@link #legacyFallback(TenantContext, VendorRepository)} with a vendor repository that knows no vendors. */
    public static MerchantScopeGuard legacyFallback(TenantContext tenantContext) {
        return legacyFallback(tenantContext, mock(VendorRepository.class));
    }
}
