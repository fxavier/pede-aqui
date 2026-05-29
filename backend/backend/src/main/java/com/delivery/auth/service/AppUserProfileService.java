package com.delivery.auth.service;

import com.delivery.auth.dto.MeResponse;
import com.delivery.auth.mapper.AppUserProfileMapper;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.MarketplaceRole;
import com.delivery.common.security.TenantContext;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contains profile lookup logic for authenticated users. */
@Service
public class AppUserProfileService {
    private final AppUserProfileRepository repository;
    private final AppUserProfileMapper mapper;
    private final TenantContext tenantContext;

    public AppUserProfileService(AppUserProfileRepository repository, AppUserProfileMapper mapper, TenantContext tenantContext) {
        this.repository = repository;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
    }

    /** Returns the current user's profile. Falls back to JWT claims for platform admins without a tenant. */
    @Transactional(readOnly = true)
    public MeResponse getCurrentUserProfile() {
        String keycloakUserId = tenantContext.currentKeycloakUserId()
                .orElseThrow(() -> new NotFoundException("Authenticated user was not found"));

        Optional<UUID> tenantIdOpt = tenantContext.currentTenantId();
        if (tenantIdOpt.isEmpty()) {
            return buildProfileFromJwt(keycloakUserId);
        }

        return repository.findByTenantIdAndKeycloakUserId(tenantIdOpt.get(), keycloakUserId)
                .map(mapper::toMeResponse)
                .orElseThrow(() -> new NotFoundException("User profile was not found"));
    }

    private MeResponse buildProfileFromJwt(String keycloakUserId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new NotFoundException("User profile was not found");
        }
        String email = jwt.getClaimAsString("email");
        String displayName = Optional.ofNullable(jwt.getClaimAsString("name"))
                .orElse(jwt.getClaimAsString("preferred_username"));
        return new MeResponse(null, null, keycloakUserId, email, displayName, null, extractRoles(jwt), null, null, null, null, null);
    }

    private Set<MarketplaceRole> extractRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) return Set.of();
        Object rolesObj = realmAccess.get("roles");
        if (!(rolesObj instanceof Collection<?> rawRoles)) return Set.of();
        return rawRoles.stream()
                .map(Object::toString)
                .flatMap(name -> {
                    try {
                        return Stream.of(MarketplaceRole.valueOf(name));
                    } catch (IllegalArgumentException e) {
                        return Stream.empty();
                    }
                })
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
}
