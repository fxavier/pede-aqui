package com.delivery.common.config;

import org.slf4j.MDC;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

/**
 * Configures the RestTemplate used for outbound HTTP (Keycloak admin API). Built from Boot's
 * {@link RestTemplateBuilder} so the auto-configured observation customizer applies (LAC-50): every call is an
 * {@code http.client.requests} metric + client span and carries the W3C {@code traceparent} header. The request's
 * {@code X-Correlation-Id} (MDC, see {@link CorrelationIdFilter}) is forwarded as well.
 */
@Configuration
public class RestTemplateConfig {
    @Bean
    RestTemplate restTemplate(RestTemplateBuilder builder) {
        // simple() = the JDK HttpURLConnection factory that `new RestTemplate()` used before, so only observation
        // and headers change, not the HTTP stack (the builder would otherwise auto-detect another client).
        return builder.requestFactoryBuilder(ClientHttpRequestFactoryBuilder.simple())
                .additionalInterceptors(correlationIdPropagation())
                .build();
    }

    /** Copies the current correlation id (if any) onto outbound requests that do not already carry one. */
    static ClientHttpRequestInterceptor correlationIdPropagation() {
        return (request, body, execution) -> {
            String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (correlationId != null && !request.getHeaders().containsKey(CorrelationIdFilter.CORRELATION_ID_HEADER)) {
                request.getHeaders().set(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);
            }
            return execution.execute(request, body);
        };
    }
}
