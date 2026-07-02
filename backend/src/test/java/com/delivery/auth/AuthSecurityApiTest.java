package com.delivery.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.security.JwtRoleConverter;
import com.delivery.common.security.SecurityConfig;
import com.delivery.tenant.controller.TenantController;
import com.delivery.tenant.service.TenantService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TenantController.class)
@Import(SecurityConfig.class)
class AuthSecurityApiTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantService tenantService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void mapsKeycloakRealmRolesToSpringAuthorities() {
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("alg", "none"),
                Map.of("sub", "user-1", "realm_access", Map.of("roles", List.of("ADMIN", "CUSTOMER"))));

        List<String> authorities = new JwtRoleConverter().convert(jwt).stream()
                .map(Object::toString)
                .toList();

        assertThat(authorities).contains("ROLE_ADMIN", "ROLE_CUSTOMER");
    }

    @Test
    void rejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/v1/tenants"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUserWithoutAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/tenants")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }
}
