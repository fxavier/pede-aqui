package com.delivery.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.PostgresApiE2ETestBase;
import com.delivery.common.security.TenancyProperties;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * LAC-48 endpoints on the production wiring: device register/unregister (any authenticated user, own tokens only,
 * token never echoed), OPS/ADMIN template list/override (audited, sensitive placeholders refused), and the masked,
 * tenant-scoped SUPPORT delivery log — with their role gates.
 */
class NotificationApiE2ETest extends PostgresApiE2ETestBase {
    private static final String TOKEN_A = "fcm-device-token-aaaaaaaaaaaa-1234";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenancyProperties tenancyProperties;

    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
    private UUID tenant;
    private UUID customerProfile;
    private String customerToken;
    private String otherCustomerToken;
    private String opsToken;
    private String supportToken;
    private String vendorToken;

    @BeforeEach
    void seed() {
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            Jwt jwt = tokens.get(invocation.<String>getArgument(0));
            if (jwt == null) throw new BadJwtException("unknown test token");
            return jwt;
        });
        tenant = tenancyProperties.getOperatorTenantId();
        String customerSub = "kc-notif-" + UUID.randomUUID();
        customerProfile = profile(customerSub);
        customerToken = customer(customerSub);
        String otherSub = "kc-notif-" + UUID.randomUUID();
        profile(otherSub);
        otherCustomerToken = customer(otherSub);
        opsToken = staff("OPS");
        supportToken = staff("SUPPORT");
        vendorToken = staff("VENDOR_ADMIN");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM device_tokens WHERE token LIKE 'fcm-device-token-%'");
        jdbc.update("DELETE FROM notification_templates WHERE tenant_id = ?", tenant);
        jdbc.update("DELETE FROM notification_deliveries WHERE idempotency_key LIKE 'api-test-%'");
    }

    @Test
    void devicesAreRegisteredIdempotentlyAndOnlyTheOwnerCanUnregister() throws Exception {
        String body = "{\"platform\":\"ANDROID\",\"token\":\"" + TOKEN_A + "\"}";
        mockMvc.perform(json(post("/api/v1/me/devices"), null, body)).andExpect(status().isUnauthorized());
        mockMvc.perform(json(post("/api/v1/me/devices"), customerToken, "{\"platform\":\"NOKIA\",\"token\":\"x\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(json(post("/api/v1/me/devices"), customerToken, body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platform").value("ANDROID"))
                .andExpect(jsonPath("$.tokenMasked").value("****1234"))
                .andExpect(jsonPath("$.token").doesNotExist());
        mockMvc.perform(json(post("/api/v1/me/devices"), customerToken, body.replace("ANDROID", "IOS"))).andExpect(status().isOk());
        assertThat(jdbc.queryForList("SELECT platform FROM device_tokens WHERE user_profile_id = ?", String.class, customerProfile))
                .containsExactly("IOS");

        // Another user's DELETE is silent and removes nothing.
        mockMvc.perform(json(delete("/api/v1/me/devices"), otherCustomerToken, "{\"token\":\"" + TOKEN_A + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(count("SELECT count(*) FROM device_tokens WHERE user_profile_id = ?", customerProfile)).isEqualTo(1);

        mockMvc.perform(json(delete("/api/v1/me/devices"), customerToken, "{\"token\":\"" + TOKEN_A + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(count("SELECT count(*) FROM device_tokens WHERE user_profile_id = ?", customerProfile)).isZero();
        mockMvc.perform(json(delete("/api/v1/me/devices"), null, "{\"token\":\"" + TOKEN_A + "\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenRegisteredByAnotherAccountMovesToTheNewAccount() throws Exception {
        String body = "{\"platform\":\"ANDROID\",\"token\":\"" + TOKEN_A + "\"}";
        mockMvc.perform(json(post("/api/v1/me/devices"), otherCustomerToken, body)).andExpect(status().isOk());
        mockMvc.perform(json(post("/api/v1/me/devices"), customerToken, body)).andExpect(status().isOk());

        assertThat(jdbc.queryForList("SELECT user_profile_id FROM device_tokens WHERE token = ?", UUID.class, TOKEN_A))
                .containsExactly(customerProfile);
    }

    @Test
    void opsListAndOverrideTemplatesWithAuditAndPlaceholderGuard() throws Exception {
        mockMvc.perform(bearer(get("/api/v1/ops/notification-templates"), customerToken)).andExpect(status().isForbidden());
        mockMvc.perform(bearer(get("/api/v1/ops/notification-templates"), supportToken)).andExpect(status().isForbidden());
        mockMvc.perform(bearer(get("/api/v1/ops/notification-templates"), opsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.templateKey=='order_accepted' && @.channel=='PUSH')].source").value("DEFAULT"))
                .andExpect(jsonPath("$[?(@.templateKey=='order_rejected' && @.channel=='SMS')].active").value(true));

        String url = "/api/v1/ops/notification-templates/order_accepted/PUSH";
        mockMvc.perform(json(put(url), opsToken, "{\"title\":\"x\",\"body\":\"O seu código é {deliveryCode}\",\"active\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("template_placeholder_not_allowed"));
        mockMvc.perform(json(put("/api/v1/ops/notification-templates/unknown_key/PUSH"), opsToken, "{\"body\":\"x\",\"active\":true}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(json(put(url), supportToken, "{\"body\":\"x\",\"active\":true}")).andExpect(status().isForbidden());

        mockMvc.perform(json(put(url), opsToken, "{\"title\":\"Aceite!\",\"body\":\"Pedido {orderReference} aceite.\",\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("TENANT"))
                .andExpect(jsonPath("$.version").value(2));
        mockMvc.perform(json(put(url), opsToken, "{\"title\":\"Aceite\",\"body\":\"Pedido {orderReference} aceite.\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(bearer(get("/api/v1/ops/notification-templates"), opsToken))
                .andExpect(jsonPath("$[?(@.templateKey=='order_accepted' && @.channel=='PUSH')].source").value("TENANT"));
        UUID overrideId = jdbc.queryForObject("SELECT id FROM notification_templates WHERE tenant_id = ? AND template_key = 'order_accepted' AND channel = 'PUSH'",
                UUID.class, tenant);
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'NOTIFICATION_TEMPLATE_UPDATED' AND target_id = ?", overrideId.toString()))
                .isEqualTo(2);
    }

    @Test
    void supportReadsAMaskedTenantScopedDeliveryLog() throws Exception {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO notification_deliveries (id, tenant_id, recipient_profile_id, channel, template_key, template_version, locale, title, body,
                    business_reference, status, attempts, next_attempt_at, recipient_masked, provider_message_id, idempotency_key, created_at, updated_at, sent_at)
                VALUES (?, ?, ?, 'SMS', 'order_rejected', 1, 'pt-PT', NULL, 'corpo privado', ?, 'SENT', 1, ?, '+25884*****67', 'sms-1', ?, ?, ?, ?)""",
                UUID.randomUUID(), tenant, customerProfile, "REF-" + customerProfile, now, "api-test-" + UUID.randomUUID(), now, now, now);

        mockMvc.perform(bearer(get("/api/v1/support/notification-deliveries?recipientProfileId=" + customerProfile + "&channel=SMS"), supportToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].recipientMasked").value("+25884*****67"))
                .andExpect(jsonPath("$.content[0].status").value("SENT"))
                .andExpect(jsonPath("$.content[0].body").doesNotExist())
                .andExpect(jsonPath("$.content[0].title").doesNotExist());
        mockMvc.perform(bearer(get("/api/v1/support/notification-deliveries?businessReference=REF-" + customerProfile + "&status=QUEUED"), supportToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(bearer(get("/api/v1/support/notification-deliveries?status=BOGUS"), supportToken)).andExpect(status().isBadRequest());
        mockMvc.perform(bearer(get("/api/v1/support/notification-deliveries"), opsToken)).andExpect(status().isOk());
        mockMvc.perform(bearer(get("/api/v1/support/notification-deliveries"), customerToken)).andExpect(status().isForbidden());
        mockMvc.perform(bearer(get("/api/v1/support/notification-deliveries"), vendorToken)).andExpect(status().isForbidden());

        // Another tenant's SUPPORT user does not see the row.
        UUID otherTenant = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', 'MZN', now(), now())",
                otherTenant, "Tenant " + otherTenant, "slug-" + otherTenant);
        mockMvc.perform(bearer(get("/api/v1/support/notification-deliveries?recipientProfileId=" + customerProfile), staffOf(otherTenant, "SUPPORT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // --- helpers ---

    private UUID profile(String sub) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'Cliente', 'ACTIVE', ?, ?)",
                id, tenant, sub, sub + "@example.test", now, now);
        jdbc.update("INSERT INTO app_user_profile_roles (app_user_profile_id, role) VALUES (?, 'CUSTOMER')", id);
        return id;
    }

    private int count(String sql, Object arg) {
        return jdbc.queryForObject(sql, Integer.class, arg);
    }

    private String customer(String sub) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", sub);
        claims.put("azp", "pede-aqui-web");
        claims.put("email", sub + "@example.test");
        claims.put("realm_access", Map.of("roles", List.of("CUSTOMER")));
        return issue(claims);
    }

    private String staff(String role) {
        return staffOf(tenant, role);
    }

    private String staffOf(UUID tenantId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", role.toLowerCase() + "-" + UUID.randomUUID());
        claims.put("azp", "delivery-app");
        claims.put("tenant_id", tenantId.toString());
        claims.put("realm_access", Map.of("roles", List.of(role)));
        return issue(claims);
    }

    private String issue(Map<String, Object> claims) {
        String value = "notif-token-" + UUID.randomUUID();
        tokens.put(value, new Jwt(value, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "RS256"), claims));
        return value;
    }

    private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return token == null ? request : request.header("Authorization", "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String token, String body) {
        return bearer(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
