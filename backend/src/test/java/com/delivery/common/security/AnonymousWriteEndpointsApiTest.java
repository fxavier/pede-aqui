package com.delivery.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.delivery.common.PostgresApiE2ETestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * LAC-02 (AS-25, SEC-06): every write endpoint (POST/PUT/PATCH/DELETE) registered in the application answers
 * 401 to an anonymous caller — the endpoints are enumerated from the live handler mapping, so a new controller
 * is covered without editing this test. The only anonymous writes are the two explicit registration POSTs and the
 * signed payment-provider webhook (W7-A).
 * Public GET browse endpoints stay open.
 */
class AnonymousWriteEndpointsApiTest extends PostgresApiE2ETestBase {
    private static final Set<String> PUBLIC_WRITES = Set.of(
            "POST /api/v1/register",
            "POST /api/v1/customers/register",
            // W7-A (LAC-31): PSP callbacks — authenticated by the provider's HMAC signature, not a JWT.
            "POST /api/v1/payments/webhooks/{provider}");
    private static final Set<RequestMethod> WRITE_METHODS = Set.of(
            RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);

    @Autowired private MockMvc mockMvc;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyWriteEndpointRejectsAnonymousCallersWith401() throws Exception {
        List<String> writes = writeEndpoints();
        assertThat(writes).as("enumerated write endpoints").hasSizeGreaterThan(50)
                .contains("POST /api/v1/catalog/categories", "PUT /api/v1/catalog/categories/{categoryId}",
                        "DELETE /api/v1/catalog/categories/{categoryId}");

        List<String> failures = new ArrayList<>();
        for (String endpoint : writes) {
            if (PUBLIC_WRITES.contains(endpoint)) {
                continue;
            }
            String[] parts = endpoint.split(" ", 2);
            String url = parts[1].replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
            int status = mockMvc.perform(request(HttpMethod.valueOf(parts[0]), url)
                            .header(TenantContext.TENANT_ID_HEADER, UUID.randomUUID().toString())
                            .contentType("application/json").content("{}"))
                    .andReturn().getResponse().getStatus();
            if (status != 401) {
                failures.add(endpoint + " -> " + status);
            }
        }
        assertThat(failures).as("anonymous writes that were not rejected with 401").isEmpty();
    }

    @Test
    void publicBrowseEndpointsStayOpenForGet() throws Exception {
        for (String url : List.of("/api/v1/catalog/categories", "/api/v1/search/vendors",
                "/api/v1/catalog/vendors/" + UUID.randomUUID() + "/products")) {
            int status = mockMvc.perform(get(url)).andReturn().getResponse().getStatus();
            assertThat(status).as("GET " + url).isNotIn(401, 403);
        }
    }

    private List<String> writeEndpoints() {
        List<String> endpoints = new ArrayList<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            Stream<RequestMethod> writeMethods = methods.isEmpty()
                    ? Stream.of(RequestMethod.POST)
                    : methods.stream().filter(WRITE_METHODS::contains);
            writeMethods.forEach(method -> info.getPatternValues().stream()
                    .filter(pattern -> pattern.startsWith("/api/"))
                    .forEach(pattern -> endpoints.add(method.name() + " " + pattern)));
        }
        return endpoints.stream().distinct().sorted().toList();
    }
}
