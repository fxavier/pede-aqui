package com.delivery.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.auth.service.CustomerProfileProvisioningService;
import com.delivery.common.config.CorrelationIdFilter;
import com.delivery.common.service.AuditLogService;
import com.delivery.tenant.repository.TenantRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Slice test of the production filter wiring (INC-11 + INC-14): CorrelationIdFilter first, then the security
 * chain, then {@link TenantRequestFilter} as registered by {@link TenancyWebConfig}. A platform admin sending a
 * malformed or unknown {@code X-Tenant-Id} gets the same RFC 9457 problem document as every other error, with
 * the correlation id in both the body and the response header.
 */
@WebMvcTest(controllers = TenantRequestFilterProblemDetailTest.StubController.class)
@Import({SecurityConfig.class, TenancyWebConfig.class, TenantContext.class, TenantRequestFilterProblemDetailTest.StubController.class})
@EnableConfigurationProperties(TenancyProperties.class)
@TestPropertySource(properties = {
        "app.tenancy.operator-tenant-id=5e6d0c1a-7b2f-4f3e-9c8a-0d1e2f3a4b5c",
        "app.tenancy.customer-clients.pede-aqui-web=5e6d0c1a-7b2f-4f3e-9c8a-0d1e2f3a4b5c"
})
class TenantRequestFilterProblemDetailTest {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired private MockMvc mockMvc;
    @Autowired private FilterRegistrationBean<TenantRequestFilter> tenantFilterRegistration;

    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private TenantRepository tenantRepository;
    @MockitoBean private AuditLogService auditLogService;
    @MockitoBean private CustomerProfileProvisioningService profileProvisioningService;
    /** The startup check needs real tenants in the database; the slice has none. */
    @MockitoBean(name = "configuredTenantsExist") private ApplicationRunner configuredTenantsExist;

    /** Platform admin: ADMIN role, no tenant_id claim. */
    private static RequestPostProcessor platformAdmin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void malformedTenantHeaderIsBadRequestProblemDocumentWithCorrelation() throws Exception {
        mockMvc.perform(get("/api/v1/stub/ok").with(platformAdmin())
                        .header(TenantContext.TENANT_ID_HEADER, "not-a-uuid")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "tenant-req-400"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, "tenant-req-400"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("X-Tenant-Id must be a tenant UUID"))
                .andExpect(jsonPath("$.instance").value("/api/v1/stub/ok"))
                .andExpect(jsonPath("$.code").value("invalid_tenant_header"))
                .andExpect(jsonPath("$.correlationId").value("tenant-req-400"))
                .andExpect(jsonPath("$.traceId").value("tenant-req-400"))
                .andExpect(jsonPath("$.message").value("X-Tenant-Id must be a tenant UUID"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.fieldErrors").isArray());
        verifyNoInteractions(tenantRepository, auditLogService);
    }

    @Test
    void unknownTenantIsNotFoundProblemDocumentWithCorrelation() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(tenantRepository.existsById(unknown)).thenReturn(false);

        mockMvc.perform(get("/api/v1/stub/ok").with(platformAdmin())
                        .header(TenantContext.TENANT_ID_HEADER, unknown.toString()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Tenant was not found"))
                .andExpect(jsonPath("$.instance").value("/api/v1/stub/ok"))
                .andExpect(jsonPath("$.code").value("tenant_not_found"))
                .andExpect(jsonPath("$.correlationId", matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.message").value("Tenant was not found"));
        verify(auditLogService, never()).log(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void knownTenantPassesThroughAndIsAudited() throws Exception {
        UUID tenant = UUID.randomUUID();
        when(tenantRepository.existsById(tenant)).thenReturn(true);

        mockMvc.perform(get("/api/v1/stub/ok").with(platformAdmin())
                        .header(TenantContext.TENANT_ID_HEADER, tenant.toString())
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "tenant-req-ok"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, "tenant-req-ok"));
        verify(auditLogService).log(eq(TenantRequestFilter.IMPERSONATION_AUDIT_ACTION), eq("TENANT"), eq(tenant.toString()),
                eq("GET /api/v1/stub/ok"), eq("ALLOWED"));
    }

    /** Security runs before the tenant filter: without a token the answer is 401, never the tenant 400. */
    @Test
    void anonymousRequestWithMalformedTenantHeaderIsUnauthorizedNotBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/stub/ok").header(TenantContext.TENANT_ID_HEADER, "not-a-uuid"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.code").value("unauthorized"));
        verifyNoInteractions(tenantRepository, auditLogService);
    }

    /** Non-admins never trigger validation: the header is ignored and the request proceeds. */
    @Test
    void malformedTenantHeaderFromStaffIsIgnored() throws Exception {
        mockMvc.perform(get("/api/v1/stub/ok")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPS"))
                                .jwt(j -> j.claim(TenantContext.TENANT_ID_CLAIM, UUID.randomUUID().toString())))
                        .header(TenantContext.TENANT_ID_HEADER, "not-a-uuid"))
                .andExpect(status().isOk());
        verifyNoInteractions(tenantRepository, auditLogService);
    }

    @Test
    void filterOrderIsCorrelationThenSecurityThenTenant() {
        Integer correlationOrder = OrderUtils.getOrder(CorrelationIdFilter.class);
        assertThat(correlationOrder).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        assertThat(tenantFilterRegistration.getOrder()).isGreaterThan(SecurityProperties.DEFAULT_FILTER_ORDER);
        assertThat(tenantFilterRegistration.getFilter()).isInstanceOf(TenantRequestFilter.class);
    }

    @RestController
    static class StubController {
        @GetMapping("/api/v1/stub/ok")
        Map<String, String> ok() {
            return Map.of("ok", "yes");
        }
    }
}
