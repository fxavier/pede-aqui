package com.delivery.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;

/**
 * Proves the real application.yml / application-{profile}.yml wiring (INC-08, SEC-07): prod refuses to start
 * without DB_PASSWORD / KEYCLOAK_ADMIN_CLIENT_SECRET, dev keeps working with its local defaults, and prod
 * disables swagger/api-docs. Secrets are forced blank via inlined properties (higher precedence than the
 * optional repo-root .env import) so the outcome never depends on the developer's machine.
 */
class ProdSecretsFailFastTest {

    private static final String[] BLANK_SECRETS = {"DB_PASSWORD=", "KEYCLOAK_ADMIN_CLIENT_SECRET="};

    private ApplicationContextRunner runner(String profile) {
        return new ApplicationContextRunner()
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profile))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
                .withUserConfiguration(RequiredSecretsProperties.class);
    }

    @Test
    void prodContextFailsWhenSecretsAreMissing() {
        runner("prod")
                .withPropertyValues(BLANK_SECRETS)
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure()).rootCause()
                            .isInstanceOf(BindValidationException.class)
                            .hasMessageContaining("dbPassword")
                            .hasMessageContaining("keycloakAdminClientSecret");
                });
    }

    @Test
    void prodValidatesSecretsBeforeAnyDataSourceIsCreated() {
        // Flyway/JPA must not open a connection before the secrets check: every DataSource depends on it.
        runner("prod")
                .withPropertyValues("DB_PASSWORD=x", "KEYCLOAK_ADMIN_CLIENT_SECRET=x")
                .withBean("dataSource", DataSource.class, () -> Mockito.mock(DataSource.class))
                .withUserConfiguration(RequiredSecretsProperties.DataSourceDependsOnRequiredSecrets.class)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBeanFactory().getBeanDefinition("dataSource").getDependsOn())
                            .contains("requiredSecretsProperties");
                });
    }

    @Test
    void prodContextFailsWhenOnlyKeycloakSecretIsMissing() {
        runner("prod")
                .withPropertyValues("DB_PASSWORD=from-env", "KEYCLOAK_ADMIN_CLIENT_SECRET=")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void prodContextStartsWhenSecretsAreProvided() {
        runner("prod")
                .withPropertyValues("DB_PASSWORD=from-env", "KEYCLOAK_ADMIN_CLIENT_SECRET=from-env")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getEnvironment().getProperty("spring.datasource.password")).isEqualTo("from-env");
                    assertThat(ctx.getEnvironment().getProperty("app.keycloak.admin.client-secret"))
                            .isEqualTo("from-env");
                });
    }

    @Test
    void prodDoesNotInheritDevSecretDefaults() {
        new ApplicationContextRunner()
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .run(ctx -> {
                    // Raw values as written in the config files loaded for prod: no literal fallback may remain.
                    List<String> rawValues = new ArrayList<>();
                    for (PropertySource<?> source : ctx.getEnvironment().getPropertySources()) {
                        if (source.getName().contains("application")) {
                            for (String key : List.of("spring.datasource.password", "app.keycloak.admin.client-secret")) {
                                Object raw = source.getProperty(key);
                                if (raw != null) {
                                    rawValues.add(key + "=" + raw);
                                }
                            }
                        }
                    }
                    assertThat(rawValues).isNotEmpty()
                            .allSatisfy(raw -> assertThat(raw).doesNotContainPattern("\\$\\{[A-Z_]+:[^}]+}"));
                });
    }

    @Test
    void devContextStartsWithLocalDefaults() {
        runner("dev").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            // @Profile("prod") keeps the fail-fast validator out of dev entirely.
            assertThat(ctx).doesNotHaveBean(RequiredSecretsProperties.class);
            assertThat(ctx.getEnvironment().getProperty("spring.datasource.password")).isNotBlank();
            assertThat(ctx.getEnvironment().getProperty("app.keycloak.admin.client-secret")).isNotBlank();
        });
    }

    @Test
    void prodDisablesSwaggerAndApiDocs() {
        runner("prod")
                .withPropertyValues("DB_PASSWORD=x", "KEYCLOAK_ADMIN_CLIENT_SECRET=x")
                .run(ctx -> {
                    assertThat(ctx.getEnvironment().getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
                    assertThat(ctx.getEnvironment().getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
                });
    }

    @Test
    void prodServesActuatorOnSeparateManagementPort() {
        runner("prod")
                .withPropertyValues("DB_PASSWORD=x", "KEYCLOAK_ADMIN_CLIENT_SECRET=x")
                .run(ctx -> {
                    assertThat(ctx.getEnvironment().getProperty("management.server.port")).isEqualTo("8091");
                    assertThat(ctx.getEnvironment().getProperty("management.endpoint.health.probes.add-additional-paths"))
                            .isEqualTo("true");
                });
    }
}
