package com.delivery.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.opentelemetry.OpenTelemetryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.otlp.OtlpTracingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.client.observation.ClientRequestObservationConvention;

/**
 * LAC-50: the tracing stack the backend ships (Micrometer Tracing + OTel bridge + OTLP exporter, Boot auto-config):
 * no endpoint → no exporter and no startup error; endpoint without a collector → exporter built, context starts,
 * spans end without throwing; tracing disabled → no exporter even with an endpoint.
 */
class TracingAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withPropertyValues("spring.application.name=delivery-marketplace")
            .withUserConfiguration(ObservabilityConfig.class)
            .withConfiguration(AutoConfigurations.of(ObservationAutoConfiguration.class, OpenTelemetryAutoConfiguration.class,
                    OpenTelemetryTracingAutoConfiguration.class, OtlpTracingAutoConfiguration.class,
                    MicrometerTracingAutoConfiguration.class));

    @Test
    void withoutEndpointThereIsATracerButNoExporter() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(Tracer.class);
            assertThat(context).doesNotHaveBean(OtlpHttpSpanExporter.class);
            assertThat(context).hasSingleBean(ClientRequestObservationConvention.class);
        });
    }

    @Test
    void withEndpointButNoCollectorTheContextStartsAndSpansEndQuietly() {
        runner.withPropertyValues("management.otlp.tracing.endpoint=http://127.0.0.1:9/v1/traces",
                        "management.otlp.tracing.timeout=200ms", "management.tracing.sampling.probability=1.0")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OtlpHttpSpanExporter.class);
                    Tracer tracer = context.getBean(Tracer.class);
                    Span span = tracer.nextSpan().name("probe").start();
                    try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
                        assertThat(tracer.currentSpan().context().traceId()).matches("[0-9a-f]{32}");
                    } finally {
                        span.end();
                    }
                });
    }

    @Test
    void tracingDisabledBuildsNoExporterEvenWithEndpoint() {
        runner.withPropertyValues("management.tracing.enabled=false", "management.otlp.tracing.endpoint=http://127.0.0.1:9/v1/traces")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(OtlpHttpSpanExporter.class);
                    assertThat(context).hasSingleBean(Tracer.class);
                });
    }

    @Test
    void outboundUriTagCollapsesUuidSegments() {
        assertThat(ObservabilityConfig.IdCollapsingClientRequestObservationConvention.collapseIds(
                "/admin/realms/delivery/users/0f8fad5b-d9cb-469f-a165-70867728950e/role-mappings/realm"))
                .isEqualTo("/admin/realms/delivery/users/{id}/role-mappings/realm");
        assertThat(ObservabilityConfig.IdCollapsingClientRequestObservationConvention.collapseIds(
                "/admin/realms/delivery/users/0F8FAD5B-D9CB-469F-A165-70867728950E"))
                .isEqualTo("/admin/realms/delivery/users/{id}");
    }
}
