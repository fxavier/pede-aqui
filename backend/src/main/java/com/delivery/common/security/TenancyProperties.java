package com.delivery.common.security;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Binds app.tenancy (ADR-0001: tenant = marketplace operator). Customer tokens carry no tenant_id; their
 * tenant is the one mapped to the OIDC client that issued the token ({@code azp}). Binding fails at
 * startup when the operator tenant or the customer client map is missing, so a misconfigured deploy
 * cannot turn every customer request into a 403.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "app.tenancy")
public class TenancyProperties {
    /** Tenant of the marketplace operator (seeded by V045); default for customer self-registration. */
    @NotNull
    private UUID operatorTenantId;

    /** Customer-facing OIDC client id ({@code azp}) → tenant id. Unlisted clients resolve no customer tenant. */
    @NotEmpty
    private Map<String, UUID> customerClients = new LinkedHashMap<>();

    /** Tenant mapped to the given customer OIDC client, if any. */
    public Optional<UUID> customerTenantFor(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(customerClients.get(clientId));
    }

    public UUID getOperatorTenantId() { return operatorTenantId; }
    public void setOperatorTenantId(UUID operatorTenantId) { this.operatorTenantId = operatorTenantId; }
    public Map<String, UUID> getCustomerClients() { return customerClients; }
    public void setCustomerClients(Map<String, UUID> customerClients) { this.customerClients = customerClients; }
}
