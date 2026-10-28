package com.delivery.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.auth.service.CustomerProfileProvisioningService;
import com.delivery.common.PostgresApiE2ETestBase;
import com.delivery.common.security.TenancyProperties;
import com.delivery.common.security.TenantContext;
import com.delivery.common.security.TenantRequestFilter;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * End-to-end API test of ADR-0001 tenant resolution (INC-11, AS-03, SEC-06) on the full application context
 * with real Postgres (Flyway, incl. the V045 operator tenant). Keycloak is replaced at its two seams: the admin
 * client (user creation) and the JwtDecoder (token validation); everything else — security chain, role
 * converter, TenantContext, TenantRequestFilter, services, repositories — is the production wiring.
 */
class CustomerTenantResolutionE2ETest extends PostgresApiE2ETestBase {



    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenancyProperties tenancyProperties;
    @Autowired private CustomerProfileProvisioningService provisioningService;

    /** Bearer token value → decoded JWT, standing in for Keycloak-issued tokens. */
    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
    private UUID operatorTenant;
    private UUID otherTenant;
    private UUID vendorId;
    private UUID skuId;

    @BeforeEach
    void seed() {
        operatorTenant = tenancyProperties.getOperatorTenantId();
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            Jwt jwt = tokens.get(invocation.<String>getArgument(0));
            if (jwt == null) throw new BadJwtException("unknown test token");
            return jwt;
        });
        Timestamp now = Timestamp.from(Instant.now());
        otherTenant = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, 'Other', ?, 'ACTIVE', 'MZN', ?, ?)",
                otherTenant, "other-" + otherTenant, now, now);
        // A vendor with one orderable SKU inside the operator tenant (ADR-0001: merchants are vendors of the operator).
        vendorId = UUID.randomUUID();
        skuId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        jdbc.update("INSERT INTO vendors (id, tenant_id, name, status, verification_status, created_at, updated_at) VALUES (?, ?, 'Loja E2E', 'ACTIVE', 'APPROVED', ?, ?)",
                vendorId, operatorTenant, now, now);
        jdbc.update("INSERT INTO categories (id, tenant_id, name, slug, active, created_at, updated_at) VALUES (?, ?, 'Mercearia', ?, TRUE, ?, ?)",
                categoryId, operatorTenant, "e2e-" + categoryId, now, now);
        jdbc.update("""
                INSERT INTO products (id, tenant_id, vendor_id, category_id, name, status,
                                      requires_prescription_metadata, manual_validation_required, prohibited_fuel, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Arroz', 'ACTIVE', FALSE, FALSE, FALSE, ?, ?)""",
                productId, operatorTenant, vendorId, categoryId, now, now);
        jdbc.update("INSERT INTO skus (id, tenant_id, product_id, sku_code, name, price, active, created_at, updated_at) VALUES (?, ?, ?, ?, 'Arroz 1kg', 18.00, TRUE, ?, ?)",
                skuId, operatorTenant, productId, "sku-" + skuId, now, now);
        jdbc.update("INSERT INTO inventory_items (id, tenant_id, vendor_id, sku_id, quantity_available, quantity_reserved, status, created_at, updated_at, version) VALUES (?, ?, ?, ?, 50, 0, 'ACTIVE', ?, ?, 0)",
                UUID.randomUUID(), operatorTenant, vendorId, skuId, now, now);
    }

    @Test
    void newCustomerRegistersLogsInAddsToCartAndChecksOutWithoutTenantHeader() throws Exception {
        String keycloakUserId = UUID.randomUUID().toString();
        String email = "cliente-" + keycloakUserId + "@example.test";
        when(keycloakAdminService.createCustomer(eq(email), anyString(), anyString(), anyString())).thenReturn(keycloakUserId);

        // 1. Register (public endpoint): Keycloak user + profile in the operator tenant.
        mockMvc.perform(post("/api/v1/customers/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Ana\",\"lastName\":\"Teste\",\"email\":\"" + email + "\",\"password\":\"segredo-123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.keycloakUserId").value(keycloakUserId));
        assertThat(profileCount(operatorTenant, keycloakUserId)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT r.role FROM app_user_profile_roles r JOIN app_user_profiles p ON p.id = r.app_user_profile_id WHERE p.keycloak_user_id = ?",
                String.class, keycloakUserId)).containsExactly("CUSTOMER");

        // 2. Login: a pede-aqui-web token with CUSTOMER, no tenant_id claim (what Keycloak issues for customers).
        String token = customerToken(keycloakUserId, email, "pede-aqui-web");

        // 3. Add to cart — no X-Tenant-Id header.
        String cart = mockMvc.perform(withBearer(post("/api/v1/customers/{customerId}/cart/items", UUID.randomUUID()), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vendorId\":\"" + vendorId + "\",\"skuId\":\"" + skuId + "\",\"quantity\":2}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID cartId = UUID.fromString(objectMapper.readTree(cart).get("id").asText());

        // 4. Checkout — no X-Tenant-Id header.
        String order = mockMvc.perform(withBearer(post("/api/v1/checkout"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartId\":\"" + cartId + "\",\"idempotencyKey\":\"e2e-" + cartId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode orderJson = objectMapper.readTree(order);
        UUID orderId = UUID.fromString(orderJson.get("id").asText());
        assertThat(jdbc.queryForObject("SELECT tenant_id FROM orders WHERE id = ?", UUID.class, orderId)).isEqualTo(operatorTenant);
        assertThat(jdbc.queryForObject("SELECT p.keycloak_user_id FROM orders o JOIN app_user_profiles p ON p.id = o.customer_id WHERE o.id = ?",
                String.class, orderId)).isEqualTo(keycloakUserId);
        assertThat(profileCount(operatorTenant, keycloakUserId)).isEqualTo(1);
    }

    @Test
    void customerWithoutAProfileIsProvisionedOnTheFirstAuthenticatedRequest() throws Exception {
        String keycloakUserId = UUID.randomUUID().toString();
        String token = customerToken(keycloakUserId, "mobile-" + keycloakUserId + "@example.test", "pede-aqui-mobile");
        assertThat(profileCount(operatorTenant, keycloakUserId)).isZero();

        addToCart(token, null).andExpect(status().isOk());

        assertThat(profileCount(operatorTenant, keycloakUserId)).isEqualTo(1);
    }

    @Test
    void customerTenantHeaderIsIgnored() throws Exception {
        String keycloakUserId = UUID.randomUUID().toString();
        String token = customerToken(keycloakUserId, "hdr-" + keycloakUserId + "@example.test", "pede-aqui-web");

        addToCart(token, otherTenant).andExpect(status().isOk());

        assertThat(profileCount(otherTenant, keycloakUserId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts c JOIN app_user_profiles p ON p.id = c.customer_id WHERE p.keycloak_user_id = ? AND c.tenant_id = ?",
                Integer.class, keycloakUserId, operatorTenant)).isEqualTo(1);
    }

    @Test
    void customerFromAnUnmappedClientGetsTenantRequiredAndNoProfile() throws Exception {
        String keycloakUserId = UUID.randomUUID().toString();
        String token = customerToken(keycloakUserId, "bo-" + keycloakUserId + "@example.test", "delivery-app");

        addToCart(token, operatorTenant)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("tenant_required"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user_profiles WHERE keycloak_user_id = ?", Integer.class, keycloakUserId)).isZero();
    }

    @Test
    void staffWithoutTenantClaimCannotPickATenantByHeader() throws Exception {
        String token = token("support-" + UUID.randomUUID(), Map.of("azp", "delivery-app"), "SUPPORT");

        mockMvc.perform(withBearer(get("/api/v1/support/tickets"), token).header(TenantContext.TENANT_ID_HEADER, otherTenant.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("tenant_required"));
    }

    @Test
    void staffWithTenantClaimKeepsItsTenantWhenSendingAnotherTenantHeader() throws Exception {
        String token = token("support-" + UUID.randomUUID(), Map.of("azp", "delivery-app", "tenant_id", operatorTenant.toString()), "SUPPORT");

        mockMvc.perform(withBearer(get("/api/v1/support/tickets"), token).header(TenantContext.TENANT_ID_HEADER, otherTenant.toString()))
                .andExpect(status().isOk());
        assertThat(impersonationAudits(otherTenant)).isZero();
    }

    @Test
    void platformAdminImpersonationIsHonouredAndAudited() throws Exception {
        String adminSub = "platform-admin-" + UUID.randomUUID();
        String token = token(adminSub, Map.of("azp", "delivery-app"), "ADMIN");

        mockMvc.perform(withBearer(get("/api/v1/admin/zones"), token).header(TenantContext.TENANT_ID_HEADER, otherTenant.toString()))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE tenant_id = ? AND actor_user_id = ? AND action = ? AND target_id = ? AND business_reference = ?",
                Integer.class, otherTenant, adminSub, TenantRequestFilter.IMPERSONATION_AUDIT_ACTION, otherTenant.toString(), "GET /api/v1/admin/zones"))
                .isEqualTo(1);
    }

    @Test
    void platformAdminCannotImpersonateAnUnknownOrMalformedTenant() throws Exception {
        String token = token("platform-admin-" + UUID.randomUUID(), Map.of("azp", "delivery-app"), "ADMIN");

        // Both are RFC 9457 problem documents with the correlation id in body and header (INC-14).
        mockMvc.perform(withBearer(get("/api/v1/admin/zones"), token).header(TenantContext.TENANT_ID_HEADER, UUID.randomUUID().toString())
                        .header("X-Correlation-Id", "e2e-tenant-404"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Correlation-Id", "e2e-tenant-404"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("tenant_not_found"))
                .andExpect(jsonPath("$.correlationId").value("e2e-tenant-404"));
        mockMvc.perform(withBearer(get("/api/v1/admin/zones"), token).header(TenantContext.TENANT_ID_HEADER, "not-a-uuid")
                        .header("X-Correlation-Id", "e2e-tenant-400"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Correlation-Id", "e2e-tenant-400"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("invalid_tenant_header"))
                .andExpect(jsonPath("$.correlationId").value("e2e-tenant-400"));
    }

    @Test
    void concurrentFirstRequestsProvisionExactlyOneProfile() throws Exception {
        String keycloakUserId = UUID.randomUUID().toString();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<UUID>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return provisioningService.ensureCustomerProfile(operatorTenant, keycloakUserId, "race@example.test", "Race");
                }));
            }
            start.countDown();
            List<UUID> ids = new ArrayList<>();
            for (Future<UUID> result : results) {
                ids.add(result.get(30, TimeUnit.SECONDS));
            }
            assertThat(ids).hasSize(threads).containsOnly(ids.get(0));
        } finally {
            pool.shutdownNow();
        }
        assertThat(profileCount(operatorTenant, keycloakUserId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user_profile_roles r JOIN app_user_profiles p ON p.id = r.app_user_profile_id WHERE p.keycloak_user_id = ?",
                Integer.class, keycloakUserId)).isEqualTo(1);
    }

    private org.springframework.test.web.servlet.ResultActions addToCart(String token, UUID tenantHeader) throws Exception {
        MockHttpServletRequestBuilder request = withBearer(post("/api/v1/customers/{customerId}/cart/items", UUID.randomUUID()), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"vendorId\":\"" + vendorId + "\",\"skuId\":\"" + skuId + "\",\"quantity\":1}");
        if (tenantHeader != null) {
            request.header(TenantContext.TENANT_ID_HEADER, tenantHeader.toString());
        }
        return mockMvc.perform(request);
    }

    private String customerToken(String subject, String email, String clientId) {
        return token(subject, Map.of("azp", clientId, "email", email, "name", "Cliente Teste"), "CUSTOMER");
    }

    private String token(String subject, Map<String, Object> extraClaims, String... realmRoles) {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        claims.put("sub", subject);
        claims.put("realm_access", Map.of("roles", List.of(realmRoles)));
        String value = "test-token-" + UUID.randomUUID();
        tokens.put(value, new Jwt(value, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "RS256"), claims));
        return value;
    }

    private static MockHttpServletRequestBuilder withBearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private int profileCount(UUID tenantId, String keycloakUserId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM app_user_profiles WHERE tenant_id = ? AND keycloak_user_id = ?",
                Integer.class, tenantId, keycloakUserId);
    }

    private int impersonationAudits(UUID tenantId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE tenant_id = ? AND action = ?",
                Integer.class, tenantId, TenantRequestFilter.IMPERSONATION_AUDIT_ACTION);
    }
}
