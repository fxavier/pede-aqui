package com.delivery.common.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Reads current tenant and user identifiers from the authenticated JWT. */
@Component
public class TenantContext {
    public static final String TENANT_ID_CLAIM = "tenant_id";
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";

    public Optional<UUID> currentTenantId() {
        Optional<UUID> jwtTenantId = currentJwt()
                .map(jwt -> jwt.getClaimAsString(TENANT_ID_CLAIM))
                .filter(value -> !value.isBlank())
                .map(UUID::fromString);
        return jwtTenantId.or(this::currentTenantIdHeader);
    }

    public Optional<String> currentKeycloakUserId() {
        return currentJwt().map(Jwt::getSubject);
    }

    private Optional<Jwt> currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        return Optional.of(jwt);
    }

    private Optional<UUID> currentTenantIdHeader() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return Optional.empty();
        }
        String value = attributes.getRequest().getHeader(TENANT_ID_HEADER);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(value));
    }
}
