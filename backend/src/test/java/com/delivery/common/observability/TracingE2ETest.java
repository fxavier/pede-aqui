package com.delivery.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.delivery.common.PostgresApiE2ETestBase;
import com.delivery.common.config.CorrelationIdFilter;
import com.delivery.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * LAC-50 end to end, with tracing ON and an OTLP endpoint that has no collector behind it (the context must start
 * anyway) and sampling at 0 (an unsampled request still has its ids): the problem document's {@code traceId} is the real W3C trace id that is in the MDC while the request is
 * handled, the JSON log line carries traceId/spanId/correlationId under the historical field names, outbound
 * RestTemplate calls carry {@code traceparent} + {@code X-Correlation-Id}, and business metrics are wired in the real
 * context. Own context (observability on), closed afterwards so its connection pool does not linger.
 */
@AutoConfigureObservability
@TestPropertySource(properties = {
        "management.otlp.tracing.endpoint=http://127.0.0.1:9/v1/traces",
        "management.otlp.tracing.timeout=200ms",
        // Unsampled on purpose: prod samples 10 %, the other 90 % must still log and return their trace id.
        "management.tracing.sampling.probability=0.0"})
@Import(TracingE2ETest.ProbeController.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TracingE2ETest extends PostgresApiE2ETestBase {
    private static final String TRACE_ID = "[0-9a-f]{32}";
    private static final String OPERATOR_TENANT = "5e6d0c1a-7b2f-4f3e-9c8a-0d1e2f3a4b5c";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ApplicationContext context;
    @Autowired private MeterRegistry meterRegistry;
    @Autowired private RestTemplate restTemplate;
    @Autowired private Environment environment;

    @Test
    void contextStartsWithExporterAndNoCollector() {
        assertThat(context.getBeansOfType(OtlpHttpSpanExporter.class)).hasSize(1);
    }

    @Test
    void problemTraceIdIsTheRequestTraceIdFromTheMdcAndTheLogLine() throws Exception {
        ch.qos.logback.classic.Logger probeLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ProbeController.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        probeLogger.addAppender(events);
        MvcResult result;
        try {
            result = mockMvc.perform(get("/api/v1/test-observability/fail")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "cid-trace-1"))
                .andExpect(status().isConflict())
                .andReturn();
        } finally {
            probeLogger.detachAppender(events);
        }

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String traceId = body.get("traceId").asText();
        assertThat(traceId).matches(TRACE_ID);
        assertThat(body.get("correlationId").asText()).isEqualTo("cid-trace-1");
        assertThat(ProbeController.MDC_SEEN.get()).containsEntry("traceId", traceId).containsEntry("correlationId", "cid-trace-1");
        assertThat(ProbeController.MDC_SEEN.get().get("spanId")).matches("[0-9a-f]{16}");

        // The logged event carries the ids; rendered with the shipped JSON config (logging.structured.* of this
        // context's Environment) — independent of how the JVM-wide console logging happened to be initialised.
        ILoggingEvent event = events.list.stream().filter(e -> "observability probe".equals(e.getFormattedMessage()))
                .findFirst().orElseThrow();
        JsonNode logLine = json(renderAsStructuredJson(event));
        assertThat(logLine.get("traceId").asText()).isEqualTo(traceId);
        assertThat(logLine.get("spanId").asText()).matches("[0-9a-f]{16}");
        assertThat(logLine.get("correlationId").asText()).isEqualTo("cid-trace-1");
        assertThat(logLine.get("msg").asText()).isEqualTo("observability probe");
        assertThat(logLine.get("level").asText()).isEqualTo("INFO");
        assertThat(logLine.get("app").asText()).isEqualTo("delivery-marketplace");
        assertThat(logLine.hasNonNull("ts")).isTrue();
        assertThat(logLine.hasNonNull("logger")).isTrue();
        assertThat(logLine.has("@version")).isFalse();
    }

    @Test
    void securityChainProblemCarriesTheTraceIdToo() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/checkout").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "cid-anon"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("traceId").asText()).matches(TRACE_ID).isNotEqualTo("cid-anon");
        assertThat(result.getResponse().getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo("cid-anon");
    }

    @Test
    void outboundRestTemplateCallsCarryTraceparentAndCorrelationId() {
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://keycloak.test/admin/realms/delivery/users/0f8fad5b-d9cb-469f-a165-70867728950e"))
                .andExpect(header("traceparent", Matchers.matchesPattern("00-" + TRACE_ID + "-[0-9a-f]{16}-0[01]")))
                .andExpect(header(CorrelationIdFilter.CORRELATION_ID_HEADER, "cid-outbound"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        MDC.put(CorrelationIdFilter.MDC_KEY, "cid-outbound");
        try {
            restTemplate.getForObject("http://keycloak.test/admin/realms/delivery/users/0f8fad5b-d9cb-469f-a165-70867728950e", String.class);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
        server.verify();
        // The user id never becomes a metric tag (IdCollapsingClientRequestObservationConvention).
        assertThat(meterRegistry.find("http.client.requests").tag("uri", "/admin/realms/delivery/users/{id}").timer()).isNotNull();
    }

    @Test
    void businessMetricsAreWiredInTheRealContext() throws Exception {
        mockMvc.perform(post("/api/v1/uploads/images/presigned-url")
                        .with(jwt().jwt(token -> token.subject("ops-observability").claim("tenant_id", OPERATOR_TENANT))
                                .authorities(new SimpleGrantedAuthority("ROLE_OPS")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/png\",\"purpose\":\"logo\",\"fileName\":\"logo\",\"contentLength\":999999999}"))
                .andExpect(status().isBadRequest());

        assertThat(meterRegistry.find("uploads.rejected").tags("stage", "presign", "kind", "image", "reason", "upload_too_large")
                .counter()).isNotNull().satisfies(counter -> assertThat(counter.count()).isGreaterThanOrEqualTo(1));
    }

    private String renderAsStructuredJson(ILoggingEvent event) {
        LoggerContext loggerContext = new LoggerContext();
        loggerContext.putObject(Environment.class.getName(), environment);
        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setContext(loggerContext);
        encoder.setFormat("logstash"); // the default outside dev (LOG_FORMAT); dev itself logs a readable pattern
        encoder.start();
        return new String(encoder.encode(event), StandardCharsets.UTF_8).trim();
    }

    private JsonNode json(String line) {
        try {
            return objectMapper.readTree(line);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Records the MDC seen inside the request and fails with a business error. */
    @RestController
    static class ProbeController {
        static final AtomicReference<Map<String, String>> MDC_SEEN = new AtomicReference<>();
        private static final Logger log = LoggerFactory.getLogger(ProbeController.class);

        @GetMapping("/api/v1/test-observability/fail")
        String fail() {
            MDC_SEEN.set(MDC.getCopyOfContextMap());
            log.info("observability probe");
            throw new BusinessException("probe_conflict", "Probe conflict", HttpStatus.CONFLICT);
        }
    }
}
