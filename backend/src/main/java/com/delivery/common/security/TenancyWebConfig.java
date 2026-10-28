package com.delivery.common.security;

import com.delivery.auth.service.CustomerProfileProvisioningService;
import com.delivery.common.service.AuditLogService;
import com.delivery.tenant.repository.TenantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Wires ADR-0001 tenant resolution: the per-request tenant filter and the startup check of configured tenants. */
@Configuration
public class TenancyWebConfig {

    /** Runs after the Spring Security chain (order -100), so the JWT principal is already in the context. */
    @Bean
    FilterRegistrationBean<TenantRequestFilter> tenantRequestFilter(TenantContext tenantContext, TenantRepository tenantRepository,
                                                                    AuditLogService auditLogService,
                                                                    CustomerProfileProvisioningService profileProvisioningService,
                                                                    ObjectMapper objectMapper) {
        FilterRegistrationBean<TenantRequestFilter> registration = new FilterRegistrationBean<>(
                new TenantRequestFilter(tenantContext, tenantRepository, auditLogService, profileProvisioningService, objectMapper));
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        return registration;
    }

    /** Fails startup when the operator tenant or a customer client's tenant does not exist (ADR-0001 risk mitigation). */
    @Bean
    ApplicationRunner configuredTenantsExist(TenancyProperties properties, TenantRepository tenantRepository) {
        return args -> {
            Set<UUID> required = new LinkedHashSet<>();
            required.add(properties.getOperatorTenantId());
            required.addAll(properties.getCustomerClients().values());
            for (UUID tenantId : required) {
                if (tenantId == null || !tenantRepository.existsById(tenantId)) {
                    throw new IllegalStateException("app.tenancy references tenant " + tenantId
                            + " which does not exist; check APP_OPERATOR_TENANT_ID / app.tenancy.customer-clients (ADR-0001)");
                }
            }
        };
    }
}
