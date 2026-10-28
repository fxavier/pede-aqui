package com.delivery.common.config;

import jakarta.validation.constraints.NotBlank;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AbstractDependsOnBeanFactoryPostProcessor;
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

    /**
     * Makes every DataSource wait for this validation, so a missing secret is reported as such before
     * Flyway/JPA touch the database (otherwise the first error would be an opaque JDBC auth failure).
     */
    @Component
    @Profile("prod")
    static class DataSourceDependsOnRequiredSecrets extends AbstractDependsOnBeanFactoryPostProcessor {
        DataSourceDependsOnRequiredSecrets() {
            super(DataSource.class, "requiredSecretsProperties");
        }
    }

    public String getDbPassword() { return dbPassword; }
    public void setDbPassword(String dbPassword) { this.dbPassword = dbPassword; }
    public String getKeycloakAdminClientSecret() { return keycloakAdminClientSecret; }
    public void setKeycloakAdminClientSecret(String keycloakAdminClientSecret) {
        this.keycloakAdminClientSecret = keycloakAdminClientSecret;
    }
}
