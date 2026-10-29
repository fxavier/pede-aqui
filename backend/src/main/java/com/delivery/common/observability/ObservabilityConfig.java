package com.delivery.common.observability;

import io.micrometer.common.KeyValue;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.client.observation.ClientRequestObservationConvention;
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention;

/**
 * Tracing/metrics wiring on top of Spring Boot's auto-configuration (LAC-50). Tracing itself (Micrometer Tracing +
 * OpenTelemetry bridge, OTLP export when {@code management.otlp.tracing.endpoint} is set) is pure auto-config.
 */
@Configuration(proxyBeanMethods = false)
public class ObservabilityConfig {

    /**
     * Propagates the observation/trace context (and thus traceId/spanId in the MDC) into the auto-configured
     * application task executor, so asynchronous {@code @ApplicationModuleListener}s continue the request's trace.
     */
    @Bean
    ContextPropagatingTaskDecorator contextPropagatingTaskDecorator() {
        return new ContextPropagatingTaskDecorator();
    }

    /**
     * Outbound HTTP metric/span naming: Keycloak admin URLs are built by concatenation, so the {@code uri} tag would
     * otherwise contain user ids. UUID path segments are collapsed to {@code {id}} to keep the tag low-cardinality.
     */
    @Bean
    ClientRequestObservationConvention clientRequestObservationConvention() {
        return new IdCollapsingClientRequestObservationConvention();
    }

    /** {@link DefaultClientRequestObservationConvention} with UUID path segments replaced by {@code {id}}. */
    static class IdCollapsingClientRequestObservationConvention extends DefaultClientRequestObservationConvention {
        private static final Pattern UUID_SEGMENT =
                Pattern.compile("/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(?=/|$|\\?)");

        @Override
        protected KeyValue uri(ClientRequestObservationContext context) {
            KeyValue uri = super.uri(context);
            return KeyValue.of(uri.getKey(), collapseIds(uri.getValue()));
        }

        static String collapseIds(String uri) {
            return uri == null ? null : UUID_SEGMENT.matcher(uri).replaceAll("/{id}");
        }
    }
}
