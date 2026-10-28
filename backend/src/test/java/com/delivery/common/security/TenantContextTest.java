package com.delivery.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Tenant resolution rules of ADR-0001 (INC-11, SEC-06): claim for staff, OIDC client for customers, header only for platform admins. */
class TenantContextTest {
    private static final UUID OPERATOR_TENANT = UUID.randomUUID();

    private TenantContext tenantContext;

    @BeforeEach
    void setUp() {
        TenancyProperties properties = new TenancyProperties();
        properties.setOperatorTenantId(OPERATOR_TENANT);
        properties.setCustomerClients(Map.of("pede-aqui-web", OPERATOR_TENANT, "pede-aqui-mobile", OPERATOR_TENANT));
        tenantContext = new TenantContext(properties);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void readsTenantIdFromJwtClaim() {
        UUID tenantId = UUID.randomUUID();
        authenticate(claims(tenantId, null), "ROLE_VENDOR_ADMIN");

        assertThat(tenantContext.currentTenantId()).contains(tenantId);
    }

    @Test
    void ignoresTenantIdHeaderForNonAdminWithoutTenantClaim() {
        authenticate(claims(null, null), "ROLE_OPS");
        withHeader(UUID.randomUUID().toString());

        assertThat(tenantContext.currentTenantId()).isEmpty();
    }

    @Test
    void headerNeverOverridesTheClaimOfATenantUser() {
        UUID own = UUID.randomUUID();
        for (String role : List.of("ROLE_OPS", "ROLE_FINANCE", "ROLE_SUPPORT", "ROLE_VENDOR_ADMIN", "ROLE_COURIER")) {
            authenticate(claims(own, null), role);
            withHeader(UUID.randomUUID().toString());

            assertThat(tenantContext.currentTenantId()).as(role).contains(own);
        }
    }

    @Test
    void tenantScopedAdminKeepsItsClaimAndIgnoresTheHeader() {
        UUID own = UUID.randomUUID();
        authenticate(claims(own, null), "ROLE_ADMIN");
        withHeader(UUID.randomUUID().toString());

        assertThat(tenantContext.isPlatformAdmin()).isFalse();
        assertThat(tenantContext.currentTenantId()).contains(own);
    }

    @Test
    void platformAdminImpersonatesThroughTheHeader() {
        UUID target = UUID.randomUUID();
        authenticate(claims(null, null), "ROLE_ADMIN");
        withHeader(target.toString());

        assertThat(tenantContext.isPlatformAdmin()).isTrue();
        assertThat(tenantContext.currentTenantId()).contains(target);
    }

    @Test
    void platformAdminWithoutHeaderOrWithMalformedHeaderHasNoTenant() {
        authenticate(claims(null, null), "ROLE_ADMIN");
        assertThat(tenantContext.currentTenantId()).isEmpty();

        withHeader("not-a-uuid");
        assertThat(tenantContext.currentTenantId()).isEmpty();
    }

    @Test
    void customerTenantComesFromTheIssuingClient() {
        authenticate(claims(null, "pede-aqui-web"), "ROLE_CUSTOMER");
        withHeader(UUID.randomUUID().toString());

        assertThat(tenantContext.isCustomerPrincipal()).isTrue();
        assertThat(tenantContext.currentTenantId()).contains(OPERATOR_TENANT);
    }

    @Test
    void customerFromAnUnmappedClientHasNoTenant() {
        authenticate(claims(null, "delivery-app"), "ROLE_CUSTOMER");
        withHeader(OPERATOR_TENANT.toString());

        assertThat(tenantContext.currentTenantId()).isEmpty();
    }

    @Test
    void customerWithADivergentTenantClaimHasNoTenant() {
        authenticate(claims(UUID.randomUUID(), "pede-aqui-web"), "ROLE_CUSTOMER");

        assertThat(tenantContext.currentTenantId()).isEmpty();
    }

    @Test
    void customerWhoIsAlsoStaffUsesTheClaim() {
        UUID own = UUID.randomUUID();
        authenticate(claims(own, "pede-aqui-web"), "ROLE_CUSTOMER", "ROLE_COURIER");

        assertThat(tenantContext.isCustomerPrincipal()).isFalse();
        assertThat(tenantContext.currentTenantId()).contains(own);
    }

    @Test
    void customersBrowseLikeAnonymousVisitors() {
        authenticate(claims(null, "pede-aqui-web"), "ROLE_CUSTOMER");
        assertThat(tenantContext.browseTenantId()).isEmpty();

        UUID own = UUID.randomUUID();
        authenticate(claims(own, null), "ROLE_VENDOR_ADMIN");
        assertThat(tenantContext.browseTenantId()).contains(own);
    }

    @Test
    void anonymousRequestIgnoresTheHeader() {
        withHeader(UUID.randomUUID().toString());

        assertThat(tenantContext.currentTenantId()).isEmpty();
    }

    private void authenticate(Map<String, Object> claims, String... authorities) {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), claims);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList(authorities)));
    }

    private void withHeader(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TenantContext.TENANT_ID_HEADER, value);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static Map<String, Object> claims(UUID tenantId, String azp) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "user-1");
        if (tenantId != null) claims.put(TenantContext.TENANT_ID_CLAIM, tenantId.toString());
        if (azp != null) claims.put(TenantContext.AUTHORIZED_PARTY_CLAIM, azp);
        return claims;
    }
}
