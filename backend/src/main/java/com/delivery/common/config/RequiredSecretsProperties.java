package com.delivery.common.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Fails prod startup when a required secret is missing (SEC-07). application-prod.yml maps each field to the
 * resolved runtime property (e.g. spring.datasource.password), and application.yml carries no literal
 * fallback for them, so an unset env var surfaces here as a blank value instead of a committed default.
 */
@Component
@Profile("prod")
@Validated
@ConfigurationProperties(prefix = "app.secrets")
public class RequiredSecretsProperties {
    /** Database password (DB_PASSWORD). */
    @NotBlank
    private String dbPassword;

    /** Client secret of the Keycloak admin service account (KEYCLOAK_ADMIN_CLIENT_SECRET). */
    @NotBlank
    private String keycloakAdminClientSecret;

    public String getDbPassword() { return dbPassword; }
    public void setDbPassword(String dbPassword) { this.dbPassword = dbPassword; }
    public String getKeycloakAdminClientSecret() { return keycloakAdminClientSecret; }
    public void setKeycloakAdminClientSecret(String keycloakAdminClientSecret) {
        this.keycloakAdminClientSecret = keycloakAdminClientSecret;
    }
}
