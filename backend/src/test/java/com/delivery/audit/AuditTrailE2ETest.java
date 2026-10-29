package com.delivery.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.PostgresApiE2ETestBase;
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
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * LAC-15 on the production wiring with real Postgres (V053): audit entries carry correlation id, remote address,
 * User-Agent and masked details; the newly covered admin actions are written; audit_logs is append-only.
 * Each test seeds its own tenant, so committed rows never collide with other E2E classes sharing the container.
 */
class AuditTrailE2ETest extends PostgresApiE2ETestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactionTemplate;

    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
    private UUID tenantId;
    private String adminSub;
    private String adminToken;

    @BeforeEach
    void seed() {
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            Jwt jwt = tokens.get(invocation.<String>getArgument(0));
            if (jwt == null) throw new BadJwtException("unknown test token");
            return jwt;
        });
        tenantId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, 'Audit', ?, 'ACTIVE', 'MZN', ?, ?)",
                tenantId, "audit-" + tenantId, now, now);
        adminSub = "tenant-admin-" + UUID.randomUUID();
        adminToken = token(adminSub, Map.of("tenant_id", tenantId.toString()), "ADMIN");
    }

    @Test
    void feePolicyChangeIsAuditedWithRequestContextAndDetails() throws Exception {
        mockMvc.perform(bearer(put("/api/v1/admin/policy"), adminToken)
                        .header("X-Correlation-Id", "corr-lac15-policy")
                        .header("User-Agent", "Backoffice-Test/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryFee\":50,\"serviceFee\":10,\"taxRate\":0.16,\"commissionRate\":0.1,\"cancellationPolicy\":\"FLEX\"}"))
                .andExpect(status().isOk());

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT actor_user_id, ip, user_agent, correlation_id, details->>'deliveryFee' AS fee, details->>'cancellationPolicy' AS policy "
                        + "FROM audit_logs WHERE tenant_id = ? AND action = 'FEE_POLICY_UPDATED'", tenantId);
        assertThat(row.get("actor_user_id")).isEqualTo(adminSub);
        assertThat(row.get("correlation_id")).isEqualTo("corr-lac15-policy");
        assertThat(row.get("user_agent")).isEqualTo("Backoffice-Test/1.0");
        assertThat(row.get("ip")).isEqualTo("127.0.0.1");
        assertThat(row.get("fee")).isEqualTo("50");
        assertThat(row.get("policy")).isEqualTo("FLEX");

        // The admin audit view exposes the new fields.
        mockMvc.perform(bearer(get("/api/v1/admin/audit"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("FEE_POLICY_UPDATED"))
                .andExpect(jsonPath("$[0].correlationId").value("corr-lac15-policy"))
                .andExpect(jsonPath("$[0].details.cancellationPolicy").value("FLEX"));
    }

    @Test
    void zoneCreationAndVendorVerificationAreAudited() throws Exception {
        mockMvc.perform(bearer(post("/api/v1/admin/zones"), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Centro\",\"geometryWkt\":\"POINT(32.58 -25.96)\"}"))
                .andExpect(status().isOk());
        assertThat(count("ZONE_CREATED")).isEqualTo(1);

        UUID vendorId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO vendors (id, tenant_id, name, status, verification_status, created_at, updated_at) VALUES (?, ?, 'Loja Audit', 'ACTIVE', 'PENDING', ?, ?)",
                vendorId, tenantId, now, now);
        mockMvc.perform(bearer(patch("/api/v1/vendors/{id}/verification", vendorId), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":false,\"reason\":\"Documentos ilegíveis\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT details->>'reason' FROM audit_logs WHERE tenant_id = ? AND action = 'VENDOR_VERIFICATION_REJECTED' AND target_id = ?",
                String.class, tenantId, vendorId.toString())).isEqualTo("Documentos ilegíveis");
    }

    @Test
    void tenantStatusChangeByThePlatformAdminIsAuditedAgainstThatTenant() throws Exception {
        String platformToken = token("platform-admin-" + UUID.randomUUID(), Map.of(), "ADMIN");

        mockMvc.perform(bearer(patch("/api/v1/tenants/{id}/status", tenantId), platformToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk());

        Map<String, Object> row = jdbc.queryForMap("SELECT details->>'from' AS f, details->>'to' AS t FROM audit_logs WHERE tenant_id = ? AND action = 'TENANT_STATUS_CHANGED'", tenantId);
        assertThat(row.get("f")).isEqualTo("ACTIVE");
        assertThat(row.get("t")).isEqualTo("SUSPENDED");
    }

    @Test
    void auditLogsIsAppendOnly() {
        UUID id = insertAuditRow();

        assertThatThrownBy(() -> jdbc.update("UPDATE audit_logs SET result = 'TAMPERED' WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_logs WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE audit_logs"))
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("append-only");
        assertThat(jdbc.queryForObject("SELECT result FROM audit_logs WHERE id = ?", String.class, id)).isEqualTo("SUCCESS");
    }

    @Test
    void retentionPurgeMayDeleteOnlyInsideAnExplicitlyFlaggedTransaction() {
        UUID id = insertAuditRow();

        Integer deleted = transactionTemplate.execute(status -> {
            jdbc.execute("SET LOCAL app.audit_logs_purge = 'on'");
            return jdbc.update("DELETE FROM audit_logs WHERE id = ?", id);
        });
        assertThat(deleted).isEqualTo(1);
        // The flag is transaction-local: a later statement on a pooled connection is protected again.
        UUID other = insertAuditRow();
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_logs WHERE id = ?", other)).isInstanceOf(DataAccessException.class);
        // UPDATE is never unlocked, not even with the purge flag.
        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            jdbc.execute("SET LOCAL app.audit_logs_purge = 'on'");
            return jdbc.update("UPDATE audit_logs SET result = 'X' WHERE id = ?", other);
        })).isInstanceOf(DataAccessException.class);
    }

    private UUID insertAuditRow() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO audit_logs (id, tenant_id, actor_user_id, action, target_type, result, created_at) VALUES (?, ?, 'test', 'TEST', 'test', 'SUCCESS', now())",
                id, tenantId);
        return id;
    }

    private int count(String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE tenant_id = ? AND action = ?", Integer.class, tenantId, action);
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
