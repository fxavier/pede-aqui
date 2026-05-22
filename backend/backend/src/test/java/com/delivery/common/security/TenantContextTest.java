package com.delivery.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class TenantContextTest {

    private final TenantContext tenantContext = new TenantContext();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void readsTenantIdFromJwtClaim() {
        UUID tenantId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwtWithTenantId(tenantId)));

        assertThat(tenantContext.currentTenantId()).contains(tenantId);
    }

    @Test
    void fallsBackToTenantIdHeaderWhenJwtClaimIsMissing() {
        UUID tenantId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwtWithoutTenantId()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TenantContext.TENANT_ID_HEADER, tenantId.toString());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(tenantContext.currentTenantId()).contains(tenantId);
    }

    private Jwt jwtWithTenantId(UUID tenantId) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), Map.of("sub", "user-1", TenantContext.TENANT_ID_CLAIM, tenantId.toString()));
    }

    private Jwt jwtWithoutTenantId() {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), Map.of("sub", "user-1"));
    }
}
