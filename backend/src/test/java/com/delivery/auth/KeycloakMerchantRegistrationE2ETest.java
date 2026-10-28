package com.delivery.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.PostgresTestContainerConfig;
import com.delivery.common.security.TenancyProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/**
 * ADR-0001 step 4 against a REAL Keycloak (Testcontainers, random ports, auto-removed): imports
 * {@code keycloak/delivery-realm.json} (so a broken realm — e.g. the new optional vendor_id mapper — fails here),
 * registers a merchant through the real {@code KeycloakAdminService}, obtains a password-grant token from the
 * {@code delivery-app} client and calls a merchant-scoped endpoint with it through the real JwtDecoder.
 */
@SpringBootTest
@AutoConfigureMockMvc
// Own context (different issuer/admin properties): close it after the class and keep its pool tiny so the shared
// Postgres container's max_connections is not exhausted by cached contexts later in the full verify.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KeycloakMerchantRegistrationE2ETest {
    private static final boolean DOCKER_AVAILABLE = DockerClientFactory.instance().isDockerAvailable();
    private static final Path REALM = Paths.get("..", "keycloak", "delivery-realm.json").toAbsolutePath().normalize();

    @SuppressWarnings("resource")
    private static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.8.1")
            .withCommand("start-dev", "--import-realm")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
            .withCopyFileToContainer(MountableFile.forHostPath(REALM), "/opt/keycloak/data/import/delivery-realm.json")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/delivery").forPort(8080).withStartupTimeout(Duration.ofMinutes(4)));

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenancyProperties tenancyProperties;

    @BeforeAll
    static void requiresDocker() {
        Assumptions.assumeTrue(DOCKER_AVAILABLE, "Skipping Keycloak E2E: Docker is required");
        Assumptions.assumeTrue(REALM.toFile().exists(), "Skipping Keycloak E2E: realm file not found at " + REALM);
    }

    @DynamicPropertySource
    static void wire(DynamicPropertyRegistry registry) {
        if (!DOCKER_AVAILABLE) {
            return;
        }
        PostgresTestContainerConfig.POSTGRES.start();
        KEYCLOAK.start();
        registry.add("spring.datasource.url", PostgresTestContainerConfig.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresTestContainerConfig.POSTGRES::getUsername);
        registry.add("spring.datasource.password", PostgresTestContainerConfig.POSTGRES::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "3");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> keycloakUrl() + "/realms/delivery");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> keycloakUrl() + "/realms/delivery/protocol/openid-connect/certs");
        registry.add("app.keycloak.admin.server-url", KeycloakMerchantRegistrationE2ETest::keycloakUrl);
        // Dev realm secret of delivery-admin-service (keycloak/delivery-realm.json); test-only.
        registry.add("app.keycloak.admin.client-secret", () -> "delivery-admin-secret");
    }

    private static String keycloakUrl() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
    }

    @Test
    void realKeycloakRegistrationThenPasswordGrantTokenResolvesTheNewVendorScope() throws Exception {
        UUID operatorTenant = tenancyProperties.getOperatorTenantId();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "loja-" + suffix + "@example.test";
        // Must satisfy the realm passwordPolicy (length 10, upper, lower, digit): the hex suffix alone may have no digit.
        String password = "Segredo1-" + suffix + "!";
        long tenantsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM tenants", Long.class);

        // 1. Register through the real admin client: Keycloak user with tenant_id attribute + VENDOR_ADMIN role.
        String body = mockMvc.perform(post("/api/v1/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Loja " + suffix + "\",\"companySlug\":\"loja-" + suffix + "\",\"defaultCurrency\":\"MZN\","
                                + "\"country\":\"MZ\",\"firstName\":\"Ana\",\"lastName\":\"Machava\",\"email\":\"" + email + "\","
                                + "\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(operatorTenant.toString()))
                .andExpect(jsonPath("$.vendorVerificationStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        UUID vendorId = UUID.fromString(objectMapper.readTree(body).get("vendorId").asText());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenants", Long.class)).isEqualTo(tenantsBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchant_users WHERE vendor_id = ? AND role = 'OWNER'", Long.class, vendorId)).isEqualTo(1);

        // 2. Login (ROPC on the backoffice client, exactly what the /login page does).
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "delivery-app");
        form.add("username", email);
        form.add("password", password);
        JsonNode tokenResponse = RestClient.create().post()
                .uri(keycloakUrl() + "/realms/delivery/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        String accessToken = tokenResponse.get("access_token").asText();
        JsonNode claims = objectMapper.readTree(Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]));
        assertThat(claims.get("tenant_id").asText()).as("tenant_id claim from the user attribute").isEqualTo(operatorTenant.toString());
        assertThat(claims.get("realm_access").get("roles").toString()).contains("VENDOR_ADMIN");
        assertThat(claims.has("vendor_id")).as("vendor_id mapper is optional: no attribute, no claim").isFalse();

        // 3. Merchant-scoped endpoint with the real token: scope resolves via merchant_users (operator tenant has many vendors).
        mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // 4. A second registration with the same email is refused by Keycloak's conflict (409 email_exists).
        mockMvc.perform(post("/api/v1/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Loja 2\",\"companySlug\":\"loja2-" + suffix + "\",\"defaultCurrency\":\"MZN\","
                                + "\"country\":\"MZ\",\"firstName\":\"Ana\",\"lastName\":\"Machava\",\"email\":\"" + email + "\","
                                + "\"password\":\"" + password + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_exists"));
    }
}
