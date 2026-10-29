package com.delivery.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.PostgresApiE2ETestBase;
import com.delivery.common.TestParents;
import com.delivery.common.security.TenancyProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W3-D on the full context with real Postgres:
 * <ul>
 *   <li>{@code GET /admin/tenants}: the platform super-admin lists every tenant; a tenant-scoped ADMIN only its own
 *       (it used to list all tenants — leak found by W2-B);</li>
 *   <li>{@code GET /orders}, {@code /orders/cursor}, {@code /orders/page}: the platform super-admin stays
 *       cross-tenant without {@code X-Tenant-Id} and is confined to the impersonated tenant with it.</li>
 * </ul>
 */
class PlatformAdminTenantScopeE2ETest extends PostgresApiE2ETestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenancyProperties tenancyProperties;

    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
    private UUID operator;
    private UUID other;
    private UUID operatorOrder;
    private UUID otherOrder;
    private String platformAdmin;
    private String tenantAdmin;

    @BeforeEach
    void seed() {
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            Jwt jwt = tokens.get(invocation.<String>getArgument(0));
            if (jwt == null) throw new BadJwtException("unknown test token");
            return jwt;
        });
        Timestamp now = Timestamp.from(Instant.now());
        operator = tenancyProperties.getOperatorTenantId();
        other = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', 'MZN', ?, ?)",
                other, "Outro operador", "w3d-" + other, now, now);
        operatorOrder = order(operator, now);
        otherOrder = order(other, now);
        platformAdmin = token("kc-platform-" + UUID.randomUUID(), null);
        tenantAdmin = token("kc-tenant-admin-" + UUID.randomUUID(), other);
    }

    @Test
    void platformAdminListsEveryTenant() throws Exception {
        JsonNode tenants = body(get("/api/v1/admin/tenants"), platformAdmin);
        assertThat(ids(tenants, "id")).contains(other);
        assertThat(tenants.size()).isGreaterThan(1);
    }

    @Test
    void tenantScopedAdminListsOnlyItsOwnTenant() throws Exception {
        JsonNode tenants = body(get("/api/v1/admin/tenants"), tenantAdmin);
        assertThat(ids(tenants, "id")).containsExactly(other);
        // X-Tenant-Id is ignored for anyone but the platform admin: it cannot widen or move the view.
        JsonNode withHeader = body(get("/api/v1/admin/tenants").header("X-Tenant-Id", operator.toString()), tenantAdmin);
        assertThat(ids(withHeader, "id")).containsExactly(other);
    }

    @Test
    void platformAdminWithoutTenantHeaderStaysCrossTenant() throws Exception {
        assertThat(ids(body(get("/api/v1/orders"), platformAdmin), "id")).contains(operatorOrder, otherOrder);
        assertThat(ids(body(get("/api/v1/orders/cursor").param("limit", "200"), platformAdmin).get("items"), "id"))
                .contains(operatorOrder, otherOrder);
        assertThat(ids(body(get("/api/v1/orders/page").param("size", "100"), platformAdmin).get("content"), "id"))
                .contains(operatorOrder, otherOrder);
    }

    @Test
    void impersonatingPlatformAdminSeesOnlyTheImpersonatedTenant() throws Exception {
        List<UUID> legacy = ids(body(get("/api/v1/orders").header("X-Tenant-Id", other.toString()), platformAdmin), "id");
        assertThat(legacy).containsExactly(otherOrder);
        List<UUID> cursor = ids(body(get("/api/v1/orders/cursor").header("X-Tenant-Id", other.toString()), platformAdmin).get("items"), "id");
        assertThat(cursor).containsExactly(otherOrder);
        JsonNode page = body(get("/api/v1/orders/page").header("X-Tenant-Id", other.toString()), platformAdmin);
        assertThat(ids(page.get("content"), "id")).containsExactly(otherOrder);
        assertThat(page.get("totalElements").asLong()).isEqualTo(1);

        assertThat(ids(body(get("/api/v1/orders").header("X-Tenant-Id", operator.toString()), platformAdmin), "id"))
                .contains(operatorOrder).doesNotContain(otherOrder);
    }

    // --- helpers ---

    private UUID order(UUID tenantId, Timestamp now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, status, subtotal, fees, taxes, discounts,
                                    discount_total, total, checkout_idempotency_key, delivery_confirmation_code_hash,
                                    delivery_confirmation_code_display, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'PAYMENT_CONFIRMED', 90, 10, 0, 0, 0, 100, ?, 'hash', '000000', ?, ?)""",
                id, tenantId, "ref-" + id, TestParents.customer(jdbc, tenantId), TestParents.vendor(jdbc, tenantId), "key-" + id, now, now);
        return id;
    }

    private JsonNode body(MockHttpServletRequestBuilder request, String token) throws Exception {
        String json = mockMvc.perform(request.header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private static List<UUID> ids(JsonNode rows, String field) {
        List<UUID> ids = new ArrayList<>();
        rows.forEach(row -> ids.add(UUID.fromString(row.get(field).asText())));
        return ids;
    }

    /** ADMIN token; {@code tenantId == null} makes it the platform super-admin (no tenant_id claim). */
    private String token(String subject, UUID tenantId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        claims.put("azp", "delivery-app");
        if (tenantId != null) {
            claims.put("tenant_id", tenantId.toString());
        }
        claims.put("realm_access", Map.of("roles", List.of("ADMIN")));
        String value = "w3d-admin-token-" + UUID.randomUUID();
        tokens.put(value, new Jwt(value, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "RS256"), claims));
        return value;
    }
}
