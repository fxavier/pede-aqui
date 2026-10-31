package com.delivery.common.exception;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.security.SecurityConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RFC 9457 error contract (INC-14, AS-17) and correlation id propagation (AS-15): every error — from the
 * MVC layer, from Spring's standard exceptions and from the security filter chain — is a problem document
 * carrying the legacy fields (code, message, correlationId, timestamp, fieldErrors, details) in parallel.
 */
@WebMvcTest(controllers = ErrorContractApiTest.StubController.class)
@Import({SecurityConfig.class, ErrorContractApiTest.StubController.class})
class ErrorContractApiTest {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static RequestPostProcessor user() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    @Test
    void businessExceptionIsProblemDocumentWithLegacyFieldsInParallel() throws Exception {
        mockMvc.perform(get("/api/v1/stub/business").with(user()).header("X-Correlation-Id", "req-123"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Correlation-Id", "req-123"))
                // RFC 9457 members
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Stock moved"))
                .andExpect(jsonPath("$.instance").value("/api/v1/stub/business"))
                // extension members
                .andExpect(jsonPath("$.code").value("stub_conflict"))
                .andExpect(jsonPath("$.correlationId").value("req-123"))
                .andExpect(jsonPath("$.traceId").value("req-123"))
                // legacy fields kept during the compatibility window
                .andExpect(jsonPath("$.message").value("Stock moved"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.details.skuId").value("sku-1"));
    }

    @Test
    void detailsAreOmittedWhenAbsent() throws Exception {
        mockMvc.perform(get("/api/v1/stub/not-found").with(user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void beanValidationFailureKeepsValidationFailedCodeAndFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/stub/validated").with(user())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    void unknownPathIsNotFoundNotInternalError() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist").with(user()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void unsupportedMethodIsMethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/api/v1/stub/business").with(user()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("method_not_allowed"))
                .andExpect(header().exists("Allow"));
    }

    @Test
    void unsupportedContentTypeIs415() throws Exception {
        mockMvc.perform(post("/api/v1/stub/validated").with(user())
                        .contentType(MediaType.TEXT_PLAIN).content("name=x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("unsupported_media_type"));
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/stub/validated").with(user())
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("malformed_request"));
    }

    @Test
    void missingRequestParameterIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/stub/param").with(user()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("missing_parameter"));
    }

    @Test
    void parameterTypeMismatchIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/stub/typed/not-a-uuid").with(user()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_parameter"));
    }

    @Test
    void dataIntegrityViolationIsNeutralConflict() throws Exception {
        mockMvc.perform(get("/api/v1/stub/integrity").with(user()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"))
                .andExpect(jsonPath("$.detail", not(containsString("uk_secret_constraint"))));
    }

    @Test
    void deadlockVictimIsConcurrentModificationNotInternalError() throws Exception {
        mockMvc.perform(get("/api/v1/stub/deadlock").with(user()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("concurrent_modification"))
                .andExpect(jsonPath("$.detail", not(containsString("deadlock"))));
    }

    @Test
    void accessDeniedInsideMvcIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/stub/denied").with(user()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void unexpectedExceptionIsInternalErrorWithoutLeakingMessage() throws Exception {
        mockMvc.perform(get("/api/v1/stub/boom").with(user()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal_error"))
                .andExpect(jsonPath("$.detail").value("Unexpected error"))
                .andExpect(jsonPath("$.message").value("Unexpected error"));
    }

    @Test
    void missingTokenFromSecurityChainIsProblemDocumentWithCorrelationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/stub/business"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(header().string("X-Correlation-Id", matchesPattern(UUID_PATTERN)))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("unauthorized"))
                .andExpect(jsonPath("$.correlationId", matchesPattern(UUID_PATTERN)));
    }

    @Test
    void urlRuleDenialFromSecurityChainIsProblemDocument() throws Exception {
        mockMvc.perform(get("/api/v1/tenants").with(user()))
                .andExpect(status().isForbidden())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void generatesCorrelationIdWhenAbsent() throws Exception {
        mockMvc.perform(get("/api/v1/stub/not-found").with(user()))
                .andExpect(header().string("X-Correlation-Id", matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.correlationId", matchesPattern(UUID_PATTERN)));
    }

    @Test
    void rejectsUnsafeIncomingCorrelationIdAndGeneratesOne() throws Exception {
        String injected = "abc\",\"level\":\"ERROR";
        mockMvc.perform(get("/api/v1/stub/not-found").with(user()).header("X-Correlation-Id", injected))
                .andExpect(header().string("X-Correlation-Id", matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.correlationId", matchesPattern(UUID_PATTERN)));

        String tooLong = "a".repeat(129);
        mockMvc.perform(get("/api/v1/stub/not-found").with(user()).header("X-Correlation-Id", tooLong))
                .andExpect(header().string("X-Correlation-Id", matchesPattern(UUID_PATTERN)));
    }

    @Test
    void successfulResponsesAlsoCarryCorrelationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/stub/ok").with(user()).header("X-Correlation-Id", "abc-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "abc-123"));
    }

    @Test
    void rateLimitIsProblemDocumentKeepingItsCode() throws Exception {
        // checkout group (application.yml): 10 POSTs per minute per account (LAC-11).
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/checkout").with(user())).andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/checkout").with(user()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("rate_limited"))
                .andExpect(jsonPath("$.message").value("Too many requests for sensitive endpoint"))
                .andExpect(jsonPath("$.correlationId").isString());
    }

    /** Throws each kind of exception the handler must translate. */
    @RestController
    static class StubController {

        record Payload(@NotBlank String name) {
        }

        @GetMapping("/api/v1/stub/ok")
        Map<String, String> ok() {
            return Map.of("ok", "yes");
        }

        @PostMapping("/api/v1/checkout")
        Map<String, String> checkout() {
            return Map.of("ok", "yes");
        }

        @GetMapping("/api/v1/stub/business")
        Map<String, String> business() {
            throw new BusinessException("stub_conflict", "Stock moved", HttpStatus.CONFLICT) {
                @Override
                public Object getDetails() {
                    return Map.of("skuId", "sku-1");
                }
            };
        }

        @GetMapping("/api/v1/stub/not-found")
        Map<String, String> notFound() {
            throw new NotFoundException("Order not found");
        }

        @PostMapping("/api/v1/stub/validated")
        Map<String, String> validated(@Valid @RequestBody Payload payload) {
            return Map.of("name", payload.name());
        }

        @GetMapping("/api/v1/stub/param")
        Map<String, String> param(@RequestParam("q") String q) {
            return Map.of("q", q);
        }

        @GetMapping("/api/v1/stub/typed/{id}")
        Map<String, String> typed(@PathVariable("id") UUID id) {
            return Map.of("id", id.toString());
        }

        @GetMapping("/api/v1/stub/deadlock")
        Map<String, String> deadlock() {
            throw new org.springframework.dao.CannotAcquireLockException("ERROR: deadlock detected on relation orders");
        }

        @GetMapping("/api/v1/stub/integrity")
        Map<String, String> integrity() {
            throw new DataIntegrityViolationException("duplicate key value violates uk_secret_constraint");
        }

        @GetMapping("/api/v1/stub/denied")
        Map<String, String> denied() {
            throw new AccessDeniedException("nope");
        }

        @GetMapping("/api/v1/stub/boom")
        Map<String, String> boom() {
            throw new IllegalStateException("db password=hunter2 leaked");
        }
    }
}
