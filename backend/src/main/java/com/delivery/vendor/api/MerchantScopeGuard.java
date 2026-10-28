package com.delivery.vendor.api;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.vendor.config.MerchantScopeProperties;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.repository.MerchantUserRepository;
import com.delivery.vendor.repository.VendorRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Single resolver of the caller's merchant scope (ADR-0001 §4, ADR-0002 C5/C6): the vendor a VENDOR_ADMIN or
 * VENDOR_STAFF principal is confined to. {@code sales}, {@code catalog}, {@code inventory}, {@code order} and
 * {@code report} delegate here instead of each keeping its own copy.
 * <p>Resolution order:
 * <ol>
 *   <li><b>merchant_users</b> (source of truth): the vendors associated with the principal in the tenant. One row →
 *       that vendor. Several rows → the optional {@code vendor_id} JWT claim selects one of them; without a claim the
 *       scope is unresolved (choosing an active vendor is out of scope of ADR-0001). A claim outside the rows is
 *       refused.</li>
 *   <li><b>Legacy fallback</b> (only while {@code app.merchant-scope.single-vendor-fallback-enabled=true}, see
 *       {@link MerchantScopeProperties} for the switch-off criterion): no merchant_users row → trust the
 *       {@code vendor_id} claim, else the tenant's single vendor, each logged as a warning.
 *       TODO(ADR-0001 step 6): delete this branch once the flag is off everywhere.</li>
 * </ol>
 * Unresolved scope always fails closed: 403 {@code vendor_scope_unresolved}.
 */
@Component
public class MerchantScopeGuard {
    public static final String VENDOR_ID_CLAIM = "vendor_id";
    public static final String SCOPE_UNRESOLVED_CODE = "vendor_scope_unresolved";
    public static final String ACCESS_DENIED_CODE = "vendor_access_denied";
    private static final Logger log = LoggerFactory.getLogger(MerchantScopeGuard.class);

    private final TenantContext tenantContext;
    private final MerchantUserRepository merchantUserRepository;
    private final VendorRepository vendorRepository;
    private final MerchantScopeProperties properties;

    public MerchantScopeGuard(TenantContext tenantContext, MerchantUserRepository merchantUserRepository,
                              VendorRepository vendorRepository, MerchantScopeProperties properties) {
        this.tenantContext = tenantContext;
        this.merchantUserRepository = merchantUserRepository;
        this.vendorRepository = vendorRepository;
        this.properties = properties;
    }

    /** True when the caller is a merchant user (VENDOR_ADMIN or VENDOR_STAFF) without a tenant-wide role. */
    public boolean isMerchantScoped() {
        return (hasAuthority("ROLE_VENDOR_ADMIN") || hasAuthority("ROLE_VENDOR_STAFF")) && !hasTenantWideRole();
    }

    /** True when the caller is a VENDOR_ADMIN without a tenant-wide role (the sales/report "vendor scoped" rule). */
    public boolean isVendorAdminScoped() {
        return hasAuthority("ROLE_VENDOR_ADMIN") && !hasTenantWideRole();
    }

    /** True for ADMIN, OPS or FINANCE — roles that see the whole tenant. */
    public boolean hasTenantWideRole() {
        return hasAuthority("ROLE_ADMIN") || hasAuthority("ROLE_OPS") || hasAuthority("ROLE_FINANCE");
    }

    /** Resolves the vendor the caller is confined to in the tenant, or empty when it cannot be determined. */
    public Optional<UUID> resolveOwnVendorId(UUID tenantId) {
        UUID claim = callerVendorClaim();
        List<UUID> associated = tenantContext.currentKeycloakUserId()
                .map(userId -> merchantUserRepository.findVendorIdsByTenantAndKeycloakUser(tenantId, userId))
                .orElse(List.of());
        if (!associated.isEmpty()) {
            if (claim != null) {
                if (associated.contains(claim)) {
                    return Optional.of(claim);
                }
                throw new BusinessException(ACCESS_DENIED_CODE, "The vendor claim does not match the caller's merchant associations", HttpStatus.FORBIDDEN);
            }
            return associated.size() == 1 ? Optional.of(associated.get(0)) : Optional.empty();
        }
        if (!properties.isSingleVendorFallbackEnabled()) {
            return Optional.empty();
        }
        // Legacy fallback (ADR-0001 step 3): kept only until the merchant_users backfill is verified everywhere.
        if (claim != null) {
            log.warn("merchant-scope fallback: trusting vendor_id claim without a merchant_users row (tenant {})", tenantId);
            return Optional.of(claim);
        }
        List<Vendor> vendors = vendorRepository.findByTenantId(tenantId);
        if (vendors.size() == 1) {
            log.warn("merchant-scope fallback: resolved the tenant's single vendor without a merchant_users row (tenant {})", tenantId);
            return Optional.of(vendors.get(0).getId());
        }
        return Optional.empty();
    }

    /** Fail-closed variant of {@link #resolveOwnVendorId}: 403 {@code vendor_scope_unresolved} when nothing resolves. */
    public UUID requireOwnVendorId(UUID tenantId) {
        return resolveOwnVendorId(tenantId).orElseThrow(() -> new BusinessException(SCOPE_UNRESOLVED_CODE,
                "Unable to resolve the vendor for the current merchant user", HttpStatus.FORBIDDEN));
    }

    /**
     * Throws 403 unless the caller's resolved vendor (in the current tenant) is {@code targetVendorId}. Callers decide
     * beforehand whether the principal is tenant-wide; this method never bypasses by role.
     */
    public void ensureCallerOwnsVendor(UUID targetVendorId, String deniedMessage) {
        UUID tenantId = tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
        UUID ownVendorId = requireOwnVendorId(tenantId);
        if (!ownVendorId.equals(targetVendorId)) {
            throw new BusinessException(ACCESS_DENIED_CODE, deniedMessage, HttpStatus.FORBIDDEN);
        }
    }

    /** True when the current authentication carries the given Spring authority (e.g. ROLE_VENDOR_ADMIN). */
    public boolean hasAuthority(String authority) {
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
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ACCESS_DENIED_CODE, "Invalid vendor claim", HttpStatus.FORBIDDEN);
        }
    }
}
