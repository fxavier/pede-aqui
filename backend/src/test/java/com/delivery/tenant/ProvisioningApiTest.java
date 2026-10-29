package com.delivery.tenant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.tenant.controller.SubscriptionProvisioningController;
import com.delivery.tenant.dto.TenantProvisioningResponse;
import com.delivery.tenant.service.SubscriptionProvisioningService;
import com.delivery.common.security.SecurityConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SubscriptionProvisioningController.class)
@Import(SecurityConfig.class)
class ProvisioningApiTest {
    private static final String VALID_BODY = """
            {"companyName":"Restaurante Costa Nova","contactName":"Ana Machava",
             "email":"ana@costanova.co.mz","phone":"+258-84-123-4567","city":"Maputo",
             "planSlug":"pro-monthly","billingCycle":"MONTHLY","externalRef":"sub_1"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubscriptionProvisioningService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private TenantProvisioningResponse response(boolean provisioned, boolean alreadyExisted) {
        return new TenantProvisioningResponse(
                UUID.randomUUID(), "restaurante-costa-nova", UUID.randomUUID(),
                "ana@costanova.co.mz", "Ana Machava", "ACTIVE", provisioned, alreadyExisted);
    }

    @Test
    void rejectsMissingTokenWith401() throws Exception {
        mockMvc.perform(post("/api/v1/provisioning/tenants")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        verify(service, never()).provision(anyString(), any());
    }

    @Test
    void rejectsTokenWithoutProvisioningRoleWith403() throws Exception {
        mockMvc.perform(post("/api/v1/provisioning/tenants")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
        verify(service, never()).provision(anyString(), any());
    }

    @Test
    void provisionsNewTenantWith201() throws Exception {
        when(service.provision(anyString(), any())).thenReturn(response(true, false));

        mockMvc.perform(post("/api/v1/provisioning/tenants")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_provisioning:write")))
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantSlug").value("restaurante-costa-nova"))
                .andExpect(jsonPath("$.provisioned").value(true))
                .andExpect(jsonPath("$.alreadyExisted").value(false));
    }

    @Test
    void replayReturns200WithExistingTenant() throws Exception {
        when(service.provision(anyString(), any())).thenReturn(response(false, true));

        mockMvc.perform(post("/api/v1/provisioning/tenants")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_provisioning:write")))
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyExisted").value(true));
    }

    @Test
    void rejectsMissingIdempotencyKeyWith400() throws Exception {
        mockMvc.perform(post("/api/v1/provisioning/tenants")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_provisioning:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        verify(service, never()).provision(anyString(), any());
    }

    @Test
    void rejectsInvalidBodyWith400() throws Exception {
        mockMvc.perform(post("/api/v1/provisioning/tenants")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_provisioning:write")))
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Costa Nova\"}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).provision(anyString(), any());
    }
}
