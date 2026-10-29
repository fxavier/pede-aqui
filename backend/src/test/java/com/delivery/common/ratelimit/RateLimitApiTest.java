package com.delivery.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.security.SecurityConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LAC-11 over HTTP with the shipped limits (application.yml, local backend in tests): anonymous registration is limited
 * per client IP (it was not limited before), the 429 is the rate_limited problem document with Retry-After, the
 * delivery-PIN bucket is per account and per route pattern (changing the id in the path does not reset it), and
 * rejections are counted in rate_limit_rejections_total{group}.
 */
@WebMvcTest(controllers = RateLimitApiTest.StubController.class)
@Import({SecurityConfig.class, RateLimitApiTest.StubController.class, RateLimitApiTest.Metrics.class})
class RateLimitApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired MeterRegistry meterRegistry;
    @MockitoBean JwtDecoder jwtDecoder;

    @TestConfiguration
    static class Metrics {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Test
    void anonymousRegistrationIsLimitedPerIpWithRetryAfterAndProblemBody() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/customers/register").with(fromIp("203.0.113.10")))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/customers/register").with(fromIp("203.0.113.10"))
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", matchesPattern("[1-9][0-9]*")))
                // Browser clients can read it (not a CORS-safelisted response header).
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("Retry-After")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.code").value("rate_limited"))
                .andExpect(jsonPath("$.details.retryAfterSeconds").isNumber())
                .andExpect(jsonPath("$.correlationId").isString());

        // Another IP has its own bucket; the merchant registration route has its own bucket too.
        mockMvc.perform(post("/api/v1/customers/register").with(fromIp("203.0.113.11"))).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/register").with(fromIp("203.0.113.10"))).andExpect(status().isOk());

        assertThat(meterRegistry.get("rate.limit.rejections").tag("group", "registration").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void spoofedForwardedForDoesNotBuyAFreshBucket() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/register").with(fromIp("203.0.113.20"))
                    .header("X-Forwarded-For", "10.9.9." + i)).andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/register").with(fromIp("203.0.113.20")).header("X-Forwarded-For", "10.9.9.99"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void deliveryPinBucketIsPerAccountAndRoutePatternNotPerUri() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/deliveries/d-" + i + "/complete").with(courier("courier-a")))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/deliveries/d-new/complete").with(courier("courier-a")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rate_limited"));

        // Another courier is unaffected.
        mockMvc.perform(post("/api/v1/deliveries/d-0/complete").with(courier("courier-b"))).andExpect(status().isOk());
    }

    @Test
    void routesOutsideEveryGroupAreNotLimited() throws Exception {
        for (int i = 0; i < 400; i++) {
            mockMvc.perform(get("/api/v1/unlimited").with(courier("courier-c"))).andExpect(status().isOk());
        }
    }

    private static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private static RequestPostProcessor courier(String sub) {
        return jwt().jwt(j -> j.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_COURIER"));
    }

    /** Stand-ins for the real controllers; only the mappings matter here. */
    @RestController
    static class StubController {
        @PostMapping({"/api/v1/register", "/api/v1/customers/register"})
        Map<String, String> register() {
            return Map.of("ok", "yes");
        }

        @PostMapping("/api/v1/deliveries/{deliveryId}/complete")
        Map<String, String> complete(@PathVariable String deliveryId) {
            return Map.of("id", deliveryId);
        }

        @GetMapping("/api/v1/unlimited")
        Map<String, String> unlimited() {
            return Map.of("ok", "yes");
        }
    }
}
