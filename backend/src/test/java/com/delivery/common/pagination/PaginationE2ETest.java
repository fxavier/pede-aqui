package com.delivery.common.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.PostgresApiE2ETestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * LAC-14 on the production wiring with real Postgres (V054): keyset cursor stability and completeness (incl. ties
 * on created_at), tenant isolation, limit/size caps, and the capped legacy arrays with {@code X-Truncated}.
 * Every test seeds fresh tenants, so committed rows never collide with other E2E classes sharing the container.
 */
class PaginationE2ETest extends PostgresApiE2ETestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
    private final Instant base = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(1, ChronoUnit.DAYS);
    private UUID tenantA;
    private UUID tenantB;
    private String adminA;

    @BeforeEach
    void seed() {
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            Jwt jwt = tokens.get(invocation.<String>getArgument(0));
            if (jwt == null) throw new BadJwtException("unknown test token");
            return jwt;
        });
        tenantA = tenant();
        tenantB = tenant();
        adminA = token("admin-" + UUID.randomUUID(), Map.of("tenant_id", tenantA.toString()), "ADMIN");
    }

    @Test
    void auditCursorWalksEveryRowOnceNewestFirstAndIsStableUnderConcurrentInserts() throws Exception {
        // Three rows share one created_at: only the id tie-break keeps them in a total order across page boundaries.
        for (int i = 0; i < 4; i++) audit(tenantA, base.plusSeconds(i));
        for (int i = 0; i < 3; i++) audit(tenantA, base.plusSeconds(10));
        for (int i = 0; i < 3; i++) audit(tenantB, base.plusSeconds(5));
        List<String> expected = jdbc.queryForList(
                "SELECT id::text FROM audit_logs WHERE tenant_id = ? ORDER BY created_at DESC, id DESC", String.class, tenantA);

        List<String> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            JsonNode page = getJson("/api/v1/admin/audit/cursor?limit=3" + (cursor == null ? "" : "&cursor=" + cursor), adminA);
            page.get("items").forEach(item -> seen.add(item.get("id").asText()));
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asText();
            if (pages++ == 0) {
                // A newer entry written after the first page must not shift or duplicate the following pages.
                audit(tenantA, Instant.now());
            }
        } while (cursor != null);

        assertThat(pages).isEqualTo(3);
        assertThat(seen).containsExactlyElementsOf(expected);
    }

    @Test
    void legacyAuditArrayIsCappedWithTruncatedHeaderAndCursorLimitIsCapped() throws Exception {
        for (int i = 0; i < 205; i++) audit(tenantA, base.plusMillis(i));

        mockMvc.perform(bearer(get("/api/v1/admin/audit"), adminA))
                .andExpect(status().isOk())
                .andExpect(header().string(CappedList.TRUNCATED_HEADER, "true"))
                .andExpect(jsonPath("$.length()").value(200));

        JsonNode first = getJson("/api/v1/admin/audit/cursor?limit=100000", adminA);
        assertThat(first.get("items")).hasSize(200);
        JsonNode second = getJson("/api/v1/admin/audit/cursor?limit=100000&cursor=" + first.get("nextCursor").asText(), adminA);
        assertThat(second.get("items")).hasSize(5);
        assertThat(second.get("nextCursor").isNull()).isTrue();

        // Under the cap: plain array, no header.
        String adminB = token("admin-" + UUID.randomUUID(), Map.of("tenant_id", tenantB.toString()), "ADMIN");
        audit(tenantB, base);
        mockMvc.perform(bearer(get("/api/v1/admin/audit"), adminB))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(CappedList.TRUNCATED_HEADER))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void malformedCursorIsA400ProblemDocument() throws Exception {
        mockMvc.perform(bearer(get("/api/v1/admin/audit/cursor").param("cursor", "not-a-cursor"), adminA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_cursor"));
    }

    @Test
    void orderCursorIsTenantIsolatedAndVendorScopedForMerchants() throws Exception {
        UUID vendorA1 = vendor(tenantA);
        UUID vendorA2 = vendor(tenantA);
        UUID customerA = profile(tenantA);
        UUID customerB = profile(tenantB);
        UUID vendorB = vendor(tenantB);
        List<UUID> tenantAOrders = new ArrayList<>();
        for (int i = 0; i < 3; i++) tenantAOrders.add(order(tenantA, vendorA1, customerA, base.plusSeconds(i)));
        for (int i = 0; i < 2; i++) tenantAOrders.add(order(tenantA, vendorA2, customerA, base.plusSeconds(20 + i)));
        order(tenantB, vendorB, customerB, base.plusSeconds(30));

        List<String> seen = walk("/api/v1/orders/cursor?limit=2", adminA);
        assertThat(seen).containsExactlyInAnyOrderElementsOf(tenantAOrders.stream().map(UUID::toString).toList());
        assertThat(seen.get(0)).isEqualTo(tenantAOrders.get(4).toString()); // newest first

        // Merchant of vendorA1 (resolved through merchant_users, ADR-0001) sees only its 3 orders.
        String merchantSub = "merchant-" + UUID.randomUUID();
        UUID merchantProfile = profile(tenantA, merchantSub);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO merchant_users (id, tenant_id, vendor_id, app_user_profile_id, role, created_at, updated_at) VALUES (?, ?, ?, ?, 'OWNER', ?, ?)",
                UUID.randomUUID(), tenantA, vendorA1, merchantProfile, now, now);
        String merchant = token(merchantSub, Map.of("tenant_id", tenantA.toString()), "VENDOR_ADMIN");
        assertThat(walk("/api/v1/orders/cursor?limit=2", merchant))
                .containsExactlyInAnyOrderElementsOf(tenantAOrders.subList(0, 3).stream().map(UUID::toString).toList());

        // The legacy array keeps its shape and scoping.
        mockMvc.perform(bearer(get("/api/v1/orders"), adminA))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(CappedList.TRUNCATED_HEADER))
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void offsetPagesCapTheSizeAndStayInsideTheTenant() throws Exception {
        for (int i = 0; i < 3; i++) ticket(tenantA, base.plusSeconds(i));
        ticket(tenantB, base);
        String support = token("support-" + UUID.randomUUID(), Map.of("tenant_id", tenantA.toString()), "SUPPORT");

        mockMvc.perform(bearer(get("/api/v1/support/tickets/page").param("size", "100000"), support))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(3));
        mockMvc.perform(bearer(get("/api/v1/support/tickets/page").param("page", "1").param("size", "2"), support))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(bearer(get("/api/v1/couriers/page").param("size", "0"), adminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(0));
        profile(tenantA);
        mockMvc.perform(bearer(get("/api/v1/admin/users/page"), adminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(PageLimits.DEFAULT_PAGE_SIZE))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void notificationCursorReturnsOnlyTheCallersOwnNotifications() throws Exception {
        String sub = "ops-" + UUID.randomUUID();
        UUID me = profile(tenantA, sub);
        UUID colleague = profile(tenantA);
        List<String> mine = new ArrayList<>();
        for (int i = 0; i < 5; i++) mine.add(notification(tenantA, me, base.plusSeconds(i)).toString());
        notification(tenantA, colleague, base.plusSeconds(100));
        String ops = token(sub, Map.of("tenant_id", tenantA.toString()), "OPS");

        List<String> seen = walk("/api/v1/notifications/cursor?limit=2", ops);

        assertThat(seen).containsExactlyElementsOf(mine.reversed());
        mockMvc.perform(bearer(get("/api/v1/notifications"), ops))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    // --- helpers ---

    private UUID notification(UUID tenantId, UUID recipient, Instant at) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO notifications (id, tenant_id, recipient_user_id, recipient_role, type, title, message, created_at) VALUES (?, ?, ?, 'OPS', 'TEST', 'T', 'M', ?)",
                id, tenantId, recipient, Timestamp.from(at));
        return id;
    }

    private List<String> walk(String path, String token) throws Exception {
        List<String> seen = new ArrayList<>();
        String cursor = null;
        do {
            JsonNode page = getJson(path + (cursor == null ? "" : "&cursor=" + cursor), token);
            page.get("items").forEach(item -> seen.add(item.get("id").asText()));
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asText();
        } while (cursor != null);
        return seen;
    }

    private JsonNode getJson(String path, String token) throws Exception {
        String body = mockMvc.perform(bearer(get(path), token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private UUID tenant() {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, 'Pag', ?, 'ACTIVE', 'MZN', ?, ?)",
                id, "pag-" + id, now, now);
        return id;
    }

    private void audit(UUID tenantId, Instant at) {
        jdbc.update("INSERT INTO audit_logs (id, tenant_id, actor_user_id, action, target_type, result, created_at) VALUES (?, ?, 'seed', 'SEED', 'test', 'SUCCESS', ?)",
                UUID.randomUUID(), tenantId, Timestamp.from(at));
    }

    private UUID vendor(UUID tenantId) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO vendors (id, tenant_id, name, status, verification_status, created_at, updated_at) VALUES (?, ?, 'Loja Pag', 'ACTIVE', 'APPROVED', ?, ?)",
                id, tenantId, now, now);
        return id;
    }

    private UUID profile(UUID tenantId) {
        return profile(tenantId, "kc-" + UUID.randomUUID());
    }

    private UUID profile(UUID tenantId, String keycloakUserId) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'Pag User', 'ACTIVE', ?, ?)",
                id, tenantId, keycloakUserId, id + "@example.test", now, now);
        return id;
    }

    private UUID order(UUID tenantId, UUID vendorId, UUID customerId, Instant at) {
        UUID id = UUID.randomUUID();
        Timestamp ts = Timestamp.from(at);
        jdbc.update("""
                INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, status, subtotal, fees, taxes, discounts,
                                    discount_total, total, checkout_idempotency_key, delivery_confirmation_code_hash,
                                    delivery_confirmation_code_display, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'PAYMENT_CONFIRMED', 90, 10, 0, 0, 0, 100, ?, 'hash', '000000', ?, ?)""",
                id, tenantId, "PAG-" + id.toString().substring(0, 8), customerId, vendorId, "pag-" + id, ts, ts);
        return id;
    }

    private void ticket(UUID tenantId, Instant at) {
        Timestamp ts = Timestamp.from(at);
        jdbc.update("INSERT INTO support_tickets (id, tenant_id, creator_user_id, subject, description, status, classification, created_at, updated_at) VALUES (?, ?, 'cust', 'S', 'D', 'OPEN', 'GENERAL', ?, ?)",
                UUID.randomUUID(), tenantId, ts, ts);
    }

    private String token(String subject, Map<String, Object> extraClaims, String... realmRoles) {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        claims.put("sub", subject);
        claims.put("realm_access", Map.of("roles", List.of(realmRoles)));
        String value = "test-token-" + UUID.randomUUID();
        tokens.put(value, new Jwt(value, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "RS256"), claims));
        return value;
    }

    private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }
}
