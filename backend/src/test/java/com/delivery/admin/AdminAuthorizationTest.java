package com.delivery.admin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.security.SecurityConfig;
import com.delivery.common.service.AuditLogService;
import com.delivery.geo.service.ZoneService;
import com.delivery.tenant.controller.AdminController;
import com.delivery.tenant.service.PolicyService;
import com.delivery.tenant.service.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminAuthorizationTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantService tenantService;
    @MockBean
    private ZoneService zoneService;
    @MockBean
    private PolicyService policyService;
    @MockBean
    private AuditLogService auditLogService;
    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void rejectsNonAdminRoles() throws Exception {
        mockMvc.perform(get("/api/v1/admin/tenants").with(jwt().authorities(() -> "ROLE_CUSTOMER"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/tenants").with(jwt().authorities(() -> "ROLE_VENDOR_ADMIN"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/tenants").with(jwt().authorities(() -> "ROLE_COURIER"))).andExpect(status().isForbidden());
    }
}
