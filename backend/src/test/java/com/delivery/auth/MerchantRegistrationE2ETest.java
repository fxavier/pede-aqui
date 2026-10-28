package com.delivery.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.PostgresApiE2ETestBase;
import com.delivery.common.security.TenancyProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

/**
 * ADR-0001 step 4 end-to-end: {@code POST /api/v1/register} creates a PENDING vendor, its OWNER profile and the
 * merchant_users row in the operator tenant — and no tenant. The new merchant's token then resolves its vendor through
 * merchant_users even though the operator tenant has several vendors.
 */
class MerchantRegistrationE2ETest extends PostgresApiE2ETestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenancyProperties tenancyProperties;

    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
    private UUID operatorTenant;

    @BeforeEach
    void setUp() {
        operatorTenant = tenancyProperties.getOperatorTenantId();
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            Jwt jwt = tokens.get(invocation.<String>getArgument(0));
            if (jwt == null) throw new BadJwtException("unknown test token");
            return jwt;
        });
        // Another vendor already lives in the operator tenant, so the single-vendor heuristic cannot resolve anything.
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO vendors (id, tenant_id, name, status, verification_status, created_at, updated_at) VALUES (?, ?, 'Outra Loja', 'ACTIVE', 'APPROVED', ?, ?)",
                UUID.randomUUID(), operatorTenant, now, now);
    }

    @Test
    void merchantRegistersIntoTheOperatorTenantAndItsTokenResolvesTheNewVendor() throws Exception {
        String keycloakUserId = "kc-merchant-" + UUID.randomUUID();
        String email = "loja-" + keycloakUserId + "@example.test";
        when(keycloakAdminService.createUser(eq(email), anyString(), anyString(), anyString(), eq(operatorTenant.toString())))
                .thenReturn(keycloakUserId);
        long tenantsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM tenants", Long.class);

        String body = mockMvc.perform(post("/api/v1/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration(email, "loja-" + keycloakUserId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(operatorTenant.toString()))
                .andExpect(jsonPath("$.vendorVerificationStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        UUID vendorId = UUID.fromString(json.get("vendorId").asText());
        UUID profileId = UUID.fromString(json.get("userProfileId").asText());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenants", Long.class)).isEqualTo(tenantsBefore);
        assertThat(jdbc.queryForMap("SELECT tenant_id, verification_status, available FROM vendors WHERE id = ?", vendorId))
                .containsEntry("tenant_id", operatorTenant)
                .containsEntry("verification_status", "PENDING")
                .containsEntry("available", false);
        assertThat(jdbc.queryForMap("SELECT tenant_id, keycloak_user_id FROM app_user_profiles WHERE id = ?", profileId))
                .containsEntry("tenant_id", operatorTenant)
                .containsEntry("keycloak_user_id", keycloakUserId);
        assertThat(jdbc.queryForList("SELECT role FROM app_user_profile_roles WHERE app_user_profile_id = ?", String.class, profileId))
                .containsExactly("VENDOR_ADMIN");
        assertThat(jdbc.queryForMap("SELECT tenant_id, vendor_id, role FROM merchant_users WHERE app_user_profile_id = ?", profileId))
                .containsEntry("tenant_id", operatorTenant)
                .containsEntry("vendor_id", vendorId)
                .containsEntry("role", "OWNER");

        // Login as the new merchant (tenant_id = operator, VENDOR_ADMIN): scope resolves via merchant_users.
        String token = merchantToken(keycloakUserId);
        mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void duplicateEmailIsRefusedWithoutCreatingAnything() throws Exception {
        String keycloakUserId = "kc-merchant-" + UUID.randomUUID();
        String email = "dup-" + keycloakUserId + "@example.test";
        when(keycloakAdminService.createUser(eq(email), anyString(), anyString(), anyString(), anyString())).thenReturn(keycloakUserId);
        mockMvc.perform(post("/api/v1/register").contentType(MediaType.APPLICATION_JSON).content(registration(email, "dup-a-" + keycloakUserId)))
                .andExpect(status().isCreated());
        long vendorsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM vendors WHERE tenant_id = ?", Long.class, operatorTenant);

        mockMvc.perform(post("/api/v1/register").contentType(MediaType.APPLICATION_JSON).content(registration(email, "dup-b-" + keycloakUserId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_exists"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vendors WHERE tenant_id = ?", Long.class, operatorTenant)).isEqualTo(vendorsBefore);
    }

    private static String registration(String email, String slug) {
        return "{\"companyName\":\"Loja Nova\",\"companySlug\":\"" + slug + "\",\"defaultCurrency\":\"MZN\",\"country\":\"MZ\","
                + "\"city\":\"Maputo\",\"firstName\":\"Ana\",\"lastName\":\"Machava\",\"email\":\"" + email + "\","
                + "\"password\":\"segredo-123\"}";
    }

    private String merchantToken(String subject) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        claims.put("azp", "delivery-app");
        claims.put("tenant_id", operatorTenant.toString());
        claims.put("realm_access", Map.of("roles", List.of("VENDOR_ADMIN")));
        String value = "reg-token-" + UUID.randomUUID();
        tokens.put(value, new Jwt(value, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "RS256"), claims));
        return value;
    }
}
