package com.delivery.vendor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds app.merchant-scope (ADR-0001 step 3). {@code single-vendor-fallback-enabled} keeps the pre-ADR heuristic
 * ("no merchant_users row → trust the vendor_id claim, else the tenant's single vendor") alive while the V049
 * backfill is being verified. Default on for this release.
 *
 * Switch-off criterion (then ADR-0001 step 6 deletes the fallback code): the ambiguity report
 * ({@code backend/scripts/adr-0001-merchant-users-report.sql}) shows zero VENDOR_ADMIN/VENDOR_STAFF profiles without
 * a merchant_users row in every environment, and the dev-data consolidation script has run where legacy
 * per-merchant tenants existed.
 */
@Component
@ConfigurationProperties(prefix = "app.merchant-scope")
public class MerchantScopeProperties {
    private boolean singleVendorFallbackEnabled = true;

    public boolean isSingleVendorFallbackEnabled() { return singleVendorFallbackEnabled; }
    public void setSingleVendorFallbackEnabled(boolean singleVendorFallbackEnabled) { this.singleVendorFallbackEnabled = singleVendorFallbackEnabled; }
}
