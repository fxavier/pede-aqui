package com.delivery.auth.service;

import com.delivery.auth.dto.CreateUserProfileRequest;
import com.delivery.auth.dto.MeResponse;
import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.mapper.AppUserProfileMapper;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.pagination.CappedList;
import com.delivery.common.pagination.PageLimits;
import com.delivery.common.pagination.PageResponse;
import com.delivery.common.security.MarketplaceRole;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditActions;
import com.delivery.common.service.AuditLogService;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contains profile lookup logic for authenticated users. */
@Service
public class AppUserProfileService {
    /**
     * LAC-13 allow-list: roles each caller role may assign. Only ADMIN assigns; ADMIN itself is added on top for
     * the platform admin only (see {@link #assignableRoles()}), because a realm ADMIN without tenant_id is a platform admin.
     */
    private static final Map<String, Set<MarketplaceRole>> ASSIGNABLE_BY_CALLER_ROLE = Map.of(
            "ROLE_ADMIN", EnumSet.complementOf(EnumSet.of(MarketplaceRole.ADMIN)));

    private final AppUserProfileRepository repository;
    private final AppUserProfileMapper mapper;
    private final TenantContext tenantContext;
    private final KeycloakAdminService keycloakAdminService;
    private final AuditLogService auditLogService;

    public AppUserProfileService(AppUserProfileRepository repository, AppUserProfileMapper mapper, TenantContext tenantContext,
                                 KeycloakAdminService keycloakAdminService, AuditLogService auditLogService) {
        this.repository = repository;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
        this.keycloakAdminService = keycloakAdminService;
        this.auditLogService = auditLogService;
    }

    /** Returns the current user's profile. Falls back to JWT claims for platform admins without a tenant. */
    @Transactional(readOnly = true)
    public MeResponse getCurrentUserProfile() {
        String keycloakUserId = tenantContext.currentKeycloakUserId()
                .orElseThrow(() -> new NotFoundException("Authenticated user was not found"));

        // Platform admins have no tenant_id in their JWT. Even when they are managing a tenant
        // (X-Tenant-Id header is present), their identity is not scoped to that tenant's profiles,
        // so skip the tenant-specific lookup to avoid a spurious 404.
        if (tenantContext.isPlatformAdmin()) {
            return repository.findByKeycloakUserId(keycloakUserId)
                    .map(mapper::toMeResponse)
                    .orElse(buildProfileFromJwt(keycloakUserId));
        }

        Optional<UUID> tenantIdOpt = tenantContext.currentTenantId();
        if (tenantIdOpt.isPresent()) {
            return repository.findByTenantIdAndKeycloakUserId(tenantIdOpt.get(), keycloakUserId)
                    .map(mapper::toMeResponse)
                    .orElseThrow(() -> new NotFoundException("User profile was not found"));
        }

        return repository.findByKeycloakUserId(keycloakUserId)
                .map(mapper::toMeResponse)
                .orElse(buildProfileFromJwt(keycloakUserId));
    }

    private MeResponse buildProfileFromJwt(String keycloakUserId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new NotFoundException("User profile was not found");
        }
        String email = jwt.getClaimAsString("email");
        String displayName = Optional.ofNullable(jwt.getClaimAsString("name"))
                .orElse(jwt.getClaimAsString("preferred_username"));
        return new MeResponse(null, null, keycloakUserId, email, displayName, null, extractRoles(jwt), null, null, null, null, null, null, null);
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

    /**
     * Creates a new user profile (admin only) with roles from the caller's allow-list (LAC-13), mirrors the roles to
     * the Keycloak user so profile and token agree, and audits the assignment. A Keycloak failure rolls back the profile.
     */
    @Transactional
    public MeResponse createUserProfile(CreateUserProfileRequest request) {
        UUID tenantId = tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context required", org.springframework.http.HttpStatus.FORBIDDEN));
        if (request.roles() == null || request.roles().isEmpty()) {
            throw new BusinessException("roles_required", "At least one role is required", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        Set<MarketplaceRole> assignable = assignableRoles();
        if (!assignable.containsAll(request.roles())) {
            throw new BusinessException("role_assignment_forbidden", "You are not allowed to assign one or more of these roles",
                    org.springframework.http.HttpStatus.FORBIDDEN);
        }

        try {
            AppUserProfile profile = new AppUserProfile(
                    UUID.randomUUID(),
                    tenantId,
                    request.keycloakUserId(),
                    request.email(),
                    request.displayName(),
                    request.roles()
            );
            
            profile.updateExtendedProfile(
                    request.fullName(),
                    request.nif(),
                    request.dateOfBirth(),
                    request.address(),
                    null
            );

            // Flush first so a duplicate (e.g. NIF) fails before Keycloak is touched.
            AppUserProfile saved = repository.saveAndFlush(profile);
            List<String> roleNames = saved.getRoles().stream().map(Enum::name).sorted().toList();
            keycloakAdminService.assignRealmRoles(saved.getKeycloakUserId(), roleNames);
            auditLogService.log(AuditActions.USER_ROLES_ASSIGNED, "user_profile", saved.getId().toString(),
                    "roles=" + String.join(",", roleNames), "SUCCESS");
            return mapper.toMeResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("nif")) {
                throw new BusinessException("nif_already_registered", "NIF already registered for this tenant", org.springframework.http.HttpStatus.CONFLICT);
            }
            throw new BusinessException("user_creation_failed", "Failed to create user profile", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** Union of the allow-lists of the caller's roles; the platform admin may additionally assign ADMIN. */
    private Set<MarketplaceRole> assignableRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Set.of();
        }
        Set<MarketplaceRole> assignable = EnumSet.noneOf(MarketplaceRole.class);
        auth.getAuthorities().forEach(a -> assignable.addAll(ASSIGNABLE_BY_CALLER_ROLE.getOrDefault(a.getAuthority(), Set.of())));
        if (!assignable.isEmpty() && tenantContext.isPlatformAdmin()) {
            assignable.add(MarketplaceRole.ADMIN);
        }
        return assignable;
    }

    /** Lists the newest user profiles of the current tenant, capped at 200 (legacy array, LAC-14; admin only). */
    @Transactional(readOnly = true)
    public CappedList<MeResponse> listUserProfiles() {
        return CappedList.of(repository.findByTenantId(requireTenantId(), PageLimits.legacyCap()), mapper::toMeResponse);
    }

    /** Lists one offset page of user profiles of the current tenant, newest first (size capped at 100). */
    @Transactional(readOnly = true)
    public PageResponse<MeResponse> listUserProfilesPage(int page, int size) {
        return PageResponse.of(repository.findByTenantId(requireTenantId(), PageLimits.page(page, size)), mapper::toMeResponse);
    }

    private UUID requireTenantId() {
        return tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context required", org.springframework.http.HttpStatus.FORBIDDEN));
    }

    /** Gets a user profile by ID (admin only). */
    @Transactional(readOnly = true)
    public MeResponse getUserProfileById(UUID id) {
        UUID tenantId = tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context required", org.springframework.http.HttpStatus.FORBIDDEN));

        return repository.findByIdAndTenantId(id, tenantId)
                .map(mapper::toMeResponse)
                .orElseThrow(() -> new NotFoundException("User profile not found"));
    }
}
