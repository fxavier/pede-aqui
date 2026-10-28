package com.delivery.common.security;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Resolves the current tenant and user from the authenticated JWT (ADR-0001, SEC-06):
 * <ul>
 *   <li>platform admin (ADMIN, no tenant_id claim): the {@code X-Tenant-Id} header, i.e. impersonation
 *       (audited by {@code TenantRequestFilter});</li>
 *   <li>customer-only principal (CUSTOMER and no internal role): the tenant mapped to the issuing OIDC
 *       client ({@code azp}) in {@link TenancyProperties}; a tenant_id claim that disagrees resolves nothing;</li>
 *   <li>everyone else (staff, merchants, couriers): the tenant_id claim only.</li>
 * </ul>
 * The header is never honoured for anyone but the platform admin. No resolvable tenant → empty, which the
 * services turn into 403 tenant_required.
 */
@Component
public class TenantContext {
    public static final String TENANT_ID_CLAIM = "tenant_id";
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";
    /** OIDC "authorized party": the client the token was issued to. */
    public static final String AUTHORIZED_PARTY_CLAIM = "azp";

    private static final String CUSTOMER_AUTHORITY = "ROLE_CUSTOMER";
    private static final Set<String> NON_CUSTOMER_AUTHORITIES = Set.of(
            "ROLE_ADMIN", "ROLE_OPS", "ROLE_FINANCE", "ROLE_SUPPORT",
            "ROLE_VENDOR_ADMIN", "ROLE_VENDOR_STAFF", "ROLE_COURIER");

    private final TenancyProperties tenancyProperties;

    public TenantContext(TenancyProperties tenancyProperties) {
        this.tenancyProperties = tenancyProperties;
    }

    public Optional<UUID> currentTenantId() {
        Optional<Jwt> jwt = currentJwt();
        if (jwt.isEmpty()) {
            return Optional.empty();
        }
        if (isPlatformAdmin()) {
            return currentTenantIdHeader();
        }
        Optional<UUID> claimTenantId = parseUuid(jwt.get().getClaimAsString(TENANT_ID_CLAIM));
        if (isCustomerPrincipal()) {
            Optional<UUID> clientTenantId = tenancyProperties.customerTenantFor(jwt.get().getClaimAsString(AUTHORIZED_PARTY_CLAIM));
            if (claimTenantId.isPresent() && !claimTenantId.equals(clientTenantId)) {
                return Optional.empty();
            }
            return clientTenantId;
        }
        return claimTenantId;
    }

    /**
     * Tenant used by the public storefront endpoints (categories, vendor products, vendor search). A customer
     * browses like an anonymous visitor — ACTIVE products across tenants; without this a customer would see
     * PENDING products of their tenant.
     * <p>TODO(ADR-0001 step 6): remove cross-tenant browsing. Once merchants are vendors of the operator tenant
     * (step 4) and legacy dev tenants are consolidated (step 5, flag {@code app.merchant-scope.single-vendor-fallback-enabled}
     * off), this must return the tenant mapped to the client/host ({@link TenancyProperties}) for anonymous and
     * customer callers alike. Contract note dated 2026-10-28 in {@code docs/api/API-MIGRATION.md}.
     */
    public Optional<UUID> browseTenantId() {
        return isCustomerPrincipal() ? Optional.empty() : currentTenantId();
    }

    public Optional<String> currentKeycloakUserId() {
        return currentJwt().map(Jwt::getSubject);
    }

    /** Returns true when the current principal has the platform ADMIN role and no tenant_id in their JWT. */
    public boolean isPlatformAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        boolean hasAdminRole = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!hasAdminRole) return false;
        String jwtTenantId = currentJwt().map(jwt -> jwt.getClaimAsString(TENANT_ID_CLAIM)).orElse(null);
        return jwtTenantId == null || jwtTenantId.isBlank();
    }

    /** True for a JWT principal holding CUSTOMER and no staff/merchant/courier role: its tenant comes from the OIDC client. */
    public boolean isCustomerPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || currentJwt().isEmpty()) return false;
        boolean customer = false;
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String name = authority.getAuthority();
            if (NON_CUSTOMER_AUTHORITIES.contains(name)) return false;
            if (CUSTOMER_AUTHORITY.equals(name)) customer = true;
        }
        return customer;
    }

    /** Raw X-Tenant-Id value of the current request, if any (not validated, not authorised). */
    public Optional<String> requestedTenantHeader() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return Optional.empty();
        }
        String value = attributes.getRequest().getHeader(TENANT_ID_HEADER);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    private Optional<Jwt> currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        return Optional.of(jwt);
    }

    private Optional<UUID> currentTenantIdHeader() {
        return requestedTenantHeader().flatMap(TenantContext::parseUuid);
    }

    private static Optional<UUID> parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
