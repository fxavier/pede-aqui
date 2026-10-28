package com.delivery.common.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Actuator exposure rules (INC-08, SEC-19): health is public; metrics/prometheus on the application port need
 * ADMIN; on the separate management port (internal network only, used by Prometheus) actuator is open.
 * Uses stub handlers on the actuator paths so only SecurityConfig decides the outcome.
 */
@WebMvcTest(controllers = ActuatorSecurityTest.StubActuatorController.class)
@Import({SecurityConfig.class, ActuatorSecurityTest.StubActuatorController.class})
@TestPropertySource(properties = {"server.port=8080", "management.server.port=9091"})
class ActuatorSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static RequestPostProcessor onPort(int port) {
        return request -> {
            request.setLocalPort(port);
            request.setServerPort(port);
            return request;
        };
    }

    @Test
    void healthIsPublicOnApplicationPort() throws Exception {
        mockMvc.perform(get("/actuator/health").with(onPort(8080))).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness").with(onPort(8080))).andExpect(status().isOk());
        mockMvc.perform(get("/livez").with(onPort(8080))).andExpect(status().isOk());
        mockMvc.perform(get("/readyz").with(onPort(8080))).andExpect(status().isOk());
    }

    @Test
    void metricsAndPrometheusRequireAuthenticationOnApplicationPort() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").with(onPort(8080))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics").with(onPort(8080))).andExpect(status().isUnauthorized());
    }

    @Test
    void metricsAndPrometheusAreForbiddenForNonAdminOnApplicationPort() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").with(onPort(8080))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/metrics").with(onPort(8080))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VENDOR_ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void metricsAreReadableByAdminOnApplicationPort() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").with(onPort(8080))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void prometheusIsScrapableWithoutTokenOnManagementPort() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").with(onPort(9091))).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health").with(onPort(9091))).andExpect(status().isOk());
    }

    @Test
    void managementPortExemptionDoesNotCoverApiPaths() throws Exception {
        mockMvc.perform(get("/api/v1/stub").with(onPort(9091))).andExpect(status().isUnauthorized());
    }

    /** Stub handlers standing in for actuator endpoints and one protected API path. */
    @RestController
    static class StubActuatorController {
        @GetMapping({"/actuator/health", "/actuator/health/readiness", "/actuator/metrics",
                "/actuator/prometheus", "/livez", "/readyz", "/api/v1/stub"})
        String ok() {
            return "ok";
        }
    }
}
