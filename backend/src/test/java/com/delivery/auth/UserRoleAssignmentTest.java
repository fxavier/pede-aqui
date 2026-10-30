package com.delivery.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.auth.dto.CreateUserProfileRequest;
import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.mapper.AppUserProfileMapper;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.auth.service.AppUserProfileService;
import com.delivery.auth.service.KeycloakAdminService;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.MarketplaceRole;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditActions;
import com.delivery.common.service.AuditLogService;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * LAC-13 (SEC-13): role assignment on POST /admin/users. Roles come from an allow-list per caller role, the
 * assignment is mirrored to Keycloak so the profile and the token agree, and it is audited.
 */
class UserRoleAssignmentTest {
    private final UUID tenantId = UUID.randomUUID();
    private AppUserProfileRepository repository;
    private TenantContext tenantContext;
    private KeycloakAdminService keycloakAdminService;
    private AuditLogService auditLogService;
    private AppUserProfileService service;

    @BeforeEach
    void setUp() {
        repository = mock(AppUserProfileRepository.class);
        tenantContext = mock(TenantContext.class);
        keycloakAdminService = mock(KeycloakAdminService.class);
        auditLogService = mock(AuditLogService.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(repository.saveAndFlush(any(AppUserProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new AppUserProfileService(repository, new AppUserProfileMapper(), tenantContext, keycloakAdminService, auditLogService);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private static void caller(String... roles) {
        String[] authorities = java.util.Arrays.stream(roles).map(r -> "ROLE_" + r).toArray(String[]::new);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("caller", "n/a", authorities));
    }

    private static CreateUserProfileRequest request(Set<MarketplaceRole> roles) {
        return new CreateUserProfileRequest("kc-user-1", "staff@example.test", "Staff", null, null, null, null, null, roles);
    }

    @Test
    @SuppressWarnings("unchecked")
    void adminAssignsRolesToProfileAndKeycloakAndAudits() {
        caller("ADMIN");

        var response = service.createUserProfile(request(Set.of(MarketplaceRole.OPS, MarketplaceRole.SUPPORT)));

        assertThat(response.roles()).containsExactlyInAnyOrder(MarketplaceRole.OPS, MarketplaceRole.SUPPORT);
        verify(repository).saveAndFlush(any(AppUserProfile.class));
        ArgumentCaptor<Collection<String>> roles = ArgumentCaptor.forClass(Collection.class);
        verify(keycloakAdminService).assignRealmRoles(eq("kc-user-1"), roles.capture());
        assertThat(roles.getValue()).containsExactlyInAnyOrder("OPS", "SUPPORT");
        ArgumentCaptor<String> reference = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).log(eq(AuditActions.USER_ROLES_ASSIGNED), eq("user_profile"), anyString(), reference.capture(), eq("SUCCESS"));
        assertThat(reference.getValue()).contains("OPS").contains("SUPPORT").doesNotContain("staff@example.test");
    }

    /** A tenant-scoped ADMIN must not mint ADMIN: a realm ADMIN without tenant_id would be a platform admin. */
    @Test
    void tenantAdminCannotAssignAdmin() {
        caller("ADMIN");
        when(tenantContext.isPlatformAdmin()).thenReturn(false);

        assertThatThrownBy(() -> service.createUserProfile(request(Set.of(MarketplaceRole.ADMIN))))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.getCode()).isEqualTo("role_assignment_forbidden");
                });
        verify(repository, never()).saveAndFlush(any());
        verify(keycloakAdminService, never()).assignRealmRoles(anyString(), any());
    }

    @Test
    void platformAdminMayAssignAdmin() {
        caller("ADMIN");
        when(tenantContext.isPlatformAdmin()).thenReturn(true);

        service.createUserProfile(request(Set.of(MarketplaceRole.ADMIN)));

        verify(keycloakAdminService).assignRealmRoles(eq("kc-user-1"), any());
    }

    /** Defence in depth behind the controller gate: SUPPORT (or anyone non-ADMIN) assigns nothing. */
    @Test
    void supportCannotAssignAnyRole() {
        caller("SUPPORT");

        assertThatThrownBy(() -> service.createUserProfile(request(Set.of(MarketplaceRole.CUSTOMER))))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("role_assignment_forbidden"));
        verify(keycloakAdminService, never()).assignRealmRoles(anyString(), any());
        verify(auditLogService, never()).log(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void emptyRoleSetIsRejected() {
        caller("ADMIN");

        assertThatThrownBy(() -> service.createUserProfile(request(Set.of())))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getCode()).isEqualTo("roles_required");
                });
    }
}
