package com.delivery.tenant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.auth.service.AppUserProfileService;
import com.delivery.common.security.SecurityConfig;
import com.delivery.common.service.AuditLogService;
import com.delivery.geo.service.ZoneService;
import com.delivery.tenant.controller.AdminController;
import com.delivery.tenant.service.PolicyService;
import com.delivery.tenant.service.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** LAC-13 (SEC-13): creating a user profile with roles is ADMIN-only; SUPPORT keeps read access to users. */
@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminUserCreationAuthorizationTest {
    private static final String BODY = "{\"keycloakUserId\":\"kc-1\",\"email\":\"staff@example.test\","
            + "\"displayName\":\"Staff\",\"roles\":[\"OPS\"]}";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TenantService tenantService;
    @MockitoBean private ZoneService zoneService;
    @MockitoBean private PolicyService policyService;
    @MockitoBean private AuditLogService auditLogService;
    @MockitoBean private AppUserProfileService appUserProfileService;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void supportCannotCreateUsers() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SUPPORT")))
                        .contentType("application/json").content(BODY))
                .andExpect(status().isForbidden());
        verify(appUserProfileService, never()).createUserProfile(any());
    }

    @Test
    void adminCreatesUsers() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType("application/json").content(BODY))
                .andExpect(status().isCreated());
        verify(appUserProfileService).createUserProfile(any());
    }

    /** Preserved behaviour: SUPPORT still lists users. */
    @Test
    void supportStillListsUsers() throws Exception {
        // LAC-14: the list is a capped array (CappedList); the mock must return one instead of null.
        org.mockito.Mockito.when(appUserProfileService.listUserProfiles())
                .thenReturn(new com.delivery.common.pagination.CappedList<>(java.util.List.of(), false));
        mockMvc.perform(get("/api/v1/admin/users").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SUPPORT"))))
                .andExpect(status().isOk());
    }
}
