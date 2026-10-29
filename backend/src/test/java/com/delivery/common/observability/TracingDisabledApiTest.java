package com.delivery.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.PostgresApiE2ETestBase;
import com.delivery.common.config.CorrelationIdFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * LAC-50, tracing disabled: the shared E2E context runs with {@code management.tracing.enabled=false} (Spring Boot's
 * test default) and no OTLP endpoint — it starts and builds no exporter; trace ids are still generated (log/MDC and
 * the problem document's traceId), only export is off. Same context key as every other
 * {@link PostgresApiE2ETestBase} subclass (no extra context).
 */
class TracingDisabledApiTest extends PostgresApiE2ETestBase {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ApplicationContext context;

    @Test
    void contextStartsWithoutExporterAndStillMintsTraceIds() throws Exception {
        assertThat(context.getEnvironment().getProperty("management.tracing.enabled")).isEqualTo("false");
        assertThat(context.getBeansOfType(OtlpHttpSpanExporter.class)).isEmpty();

        MvcResult result = mockMvc.perform(post("/api/v1/checkout").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "cid-no-tracing"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("correlationId").asText()).isEqualTo("cid-no-tracing");
        // Boot 3.5: tracing disabled = nothing exported, but the tracer still mints ids, so the body keeps a real trace id.
        assertThat(body.get("traceId").asText()).matches("[0-9a-f]{32}");
    }
}
