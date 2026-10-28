package com.delivery.common;

import com.delivery.auth.service.KeycloakAdminService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.DockerClientFactory;

/**
 * Shared configuration for full-context API end-to-end tests on Testcontainers Postgres. Keycloak is replaced
 * only at its two seams (admin client and JwtDecoder). Every subclass gets the identical context key, so Spring
 * caches ONE application context (and one connection pool) for all of them instead of one per test class, which
 * otherwise exhausts the container's max_connections in a full `mvn verify`.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class PostgresApiE2ETestBase {
    protected static final boolean DOCKER_AVAILABLE = DockerClientFactory.instance().isDockerAvailable();

    @MockitoBean protected KeycloakAdminService keycloakAdminService;
    @MockitoBean protected JwtDecoder jwtDecoder;

    @BeforeAll
    static void requiresDockerOrExternalDatabase() {
        Assumptions.assumeTrue(DOCKER_AVAILABLE || System.getenv("SPRING_DATASOURCE_URL") != null,
                "Skipping API E2E test: no Docker environment and no SPRING_DATASOURCE_URL override");
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        if (!DOCKER_AVAILABLE) {
            return;
        }
        PostgresTestContainerConfig.POSTGRES.start();
        registry.add("spring.datasource.url", PostgresTestContainerConfig.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresTestContainerConfig.POSTGRES::getUsername);
        registry.add("spring.datasource.password", PostgresTestContainerConfig.POSTGRES::getPassword);
    }
}
