package com.delivery.common.idempotency;

import com.delivery.common.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Registers the Idempotency-Key filter (LAC-24) after the security chain and the tenant filter. */
@Configuration
public class IdempotencyConfig {

    /** Order LOWEST_PRECEDENCE - 5: after TenantRequestFilter (-10), so tenant headers are already validated. */
    @Bean
    FilterRegistrationBean<IdempotencyFilter> idempotencyFilter(IdempotencyProperties properties, IdempotencyRecordRepository repository,
                                                                TenantContext tenantContext, ObjectMapper objectMapper) {
        FilterRegistrationBean<IdempotencyFilter> registration = new FilterRegistrationBean<>(
                new IdempotencyFilter(properties, repository, tenantContext, objectMapper));
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 5);
        return registration;
    }
}
