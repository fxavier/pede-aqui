package com.delivery.vendor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * ADR-0001 step 3 end-to-end on the full context with real Postgres: two vendors in the operator tenant, merchant
 * users associated through merchant_users. VENDOR_ADMIN of A and VENDOR_STAFF of B cannot touch each other's
 * orders, products or stock; a merchant without association in a multi-vendor tenant fails closed.
 */
class MerchantScopeE2ETest extends PostgresApiE2ETestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenancyProperties tenancyProperties;

    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
    private UUID tenant;
    private UUID categoryId;
    private UUID vendorA;
    private UUID vendorB;
    private UUID inventoryA;
    private UUID inventoryB;
    private UUID orderA;
    private UUID orderB;
    private String adminA;
    private String staffB;
    private String loneAdmin;

    @BeforeEach
    void seed() {
        tenant = tenancyProperties.getOperatorTenantId();
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            Jwt jwt = tokens.get(invocation.<String>getArgument(0));
            if (jwt == null) throw new BadJwtException("unknown test token");
            return jwt;
        });
        Timestamp now = Timestamp.from(Instant.now());
        categoryId = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, tenant_id, name, slug, active, created_at, updated_at) VALUES (?, ?, 'Mercearia', ?, TRUE, ?, ?)",
                categoryId, tenant, "scope-" + categoryId, now, now);
        vendorA = vendor("Loja A", now);
        vendorB = vendor("Loja B", now);
        inventoryA = stock(vendorA, now);
        inventoryB = stock(vendorB, now);
        orderA = order(vendorA, now);
        orderB = order(vendorB, now);

        adminA = merchant("VENDOR_ADMIN", vendorA, "OWNER", now);
        staffB = merchant("VENDOR_STAFF", vendorB, "STAFF", now);
        loneAdmin = merchant("VENDOR_ADMIN", null, null, now);
    }

    @Test
    void vendorAdminListsAndActsOnlyOnOwnVendorOrders() throws Exception {
        String token = token(adminA, "VENDOR_ADMIN", null);

        String body = mockMvc.perform(withBearer(get("/api/v1/orders"), token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<UUID> ids = new ArrayList<>();
        for (JsonNode row : objectMapper.readTree(body)) {
            ids.add(UUID.fromString(row.get("id").asText()));
        }
        assertThat(ids).contains(orderA).doesNotContain(orderB);

        mockMvc.perform(withBearer(patch("/api/v1/vendor/orders/{id}/accept", orderB), token))
                .andExpect(status().isNotFound());
        assertThat(orderStatus(orderB)).isEqualTo("PAYMENT_CONFIRMED");
        mockMvc.perform(withBearer(patch("/api/v1/vendor/orders/{id}/accept", orderA), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED_BY_VENDOR"));

        mockMvc.perform(withBearer(get("/api/v1/sales/orders"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void vendorStaffStockUpdatesAreConfinedToTheAssociatedVendor() throws Exception {
        String token = token(staffB, "VENDOR_STAFF", null);

        mockMvc.perform(withBearer(patch("/api/v1/inventory/{id}/stock", inventoryA), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantityAvailable\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("vendor_access_denied"));
        assertThat(jdbc.queryForObject("SELECT quantity_available FROM inventory_items WHERE id = ?", Integer.class, inventoryA)).isEqualTo(50);

        mockMvc.perform(withBearer(patch("/api/v1/inventory/{id}/stock", inventoryB), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantityAvailable\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityAvailable").value(7));
    }

    @Test
    void vendorAdminCannotCreateProductsForAnotherVendor() throws Exception {
        String token = token(adminA, "VENDOR_ADMIN", null);

        mockMvc.perform(withBearer(post("/api/v1/catalog/products"), token)
                        .contentType(MediaType.APPLICATION_JSON).content(productJson(vendorB)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("vendor_access_denied"));
        mockMvc.perform(withBearer(post("/api/v1/catalog/products"), token)
                        .contentType(MediaType.APPLICATION_JSON).content(productJson(vendorA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vendorId").value(vendorA.toString()));
    }

    @Test
    void vendorAdminReportsNeverIncludeAnotherVendor() throws Exception {
        String token = token(adminA, "VENDOR_ADMIN", null);

        String body = mockMvc.perform(withBearer(get("/api/v1/reports/sales/by-vendor"), token)
                        .param("from", "2026-01-01T00:00:00Z").param("to", "2027-01-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode row : objectMapper.readTree(body)) {
            assertThat(row.get("key").asText()).isNotEqualTo(vendorB.toString());
        }
    }

    @Test
    void merchantWithoutAssociationInAMultiVendorTenantFailsClosed() throws Exception {
        String token = token(loneAdmin, "VENDOR_ADMIN", null);

        mockMvc.perform(withBearer(get("/api/v1/orders"), token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("vendor_scope_unresolved"));
        mockMvc.perform(withBearer(patch("/api/v1/vendor/orders/{id}/accept", orderA), token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("vendor_scope_unresolved"));
        assertThat(orderStatus(orderA)).isEqualTo("PAYMENT_CONFIRMED");
    }

    @Test
    void vendorClaimOutsideTheCallersAssociationsIsRefused() throws Exception {
        String token = token(staffB, "VENDOR_STAFF", vendorA);

        mockMvc.perform(withBearer(patch("/api/v1/inventory/{id}/stock", inventoryB), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantityAvailable\":3}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("vendor_access_denied"));
        mockMvc.perform(withBearer(patch("/api/v1/inventory/{id}/stock", inventoryA), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantityAvailable\":3}"))
                .andExpect(status().isForbidden());
    }

    // --- seed helpers ---

    private UUID vendor(String name, Timestamp now) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO vendors (id, tenant_id, name, status, verification_status, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', 'APPROVED', ?, ?)",
                id, tenant, name, now, now);
        return id;
    }

    private UUID stock(UUID vendorId, Timestamp now) {
        UUID productId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO products (id, tenant_id, vendor_id, category_id, name, status,
                                      requires_prescription_metadata, manual_validation_required, prohibited_fuel, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Arroz', 'ACTIVE', FALSE, FALSE, FALSE, ?, ?)""",
                productId, tenant, vendorId, categoryId, now, now);
        jdbc.update("INSERT INTO skus (id, tenant_id, product_id, sku_code, name, price, active, created_at, updated_at) VALUES (?, ?, ?, ?, 'Arroz 1kg', 18.00, TRUE, ?, ?)",
                skuId, tenant, productId, "sku-" + skuId, now, now);
        jdbc.update("INSERT INTO inventory_items (id, tenant_id, vendor_id, sku_id, quantity_available, quantity_reserved, status, created_at, updated_at, version) VALUES (?, ?, ?, ?, 50, 0, 'ACTIVE', ?, ?, 0)",
                itemId, tenant, vendorId, skuId, now, now);
        return itemId;
    }

    private UUID order(UUID vendorId, Timestamp now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, status, subtotal, fees, taxes, discounts,
                                    discount_total, total, checkout_idempotency_key, delivery_confirmation_code_hash,
                                    delivery_confirmation_code_display, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'PAYMENT_CONFIRMED', 90, 10, 0, 0, 0, 100, ?, 'hash', '000000', ?, ?)""",
                id, tenant, "ref-" + id, TestParents.customer(jdbc, tenant), vendorId, "key-" + id, now, now);
        return id;
    }

    /** Creates a merchant profile in the operator tenant and, when a vendor is given, its merchant_users row. */
    private String merchant(String role, UUID vendorId, String merchantRole, Timestamp now) {
        String keycloakUserId = "kc-" + UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'Merchant', 'ACTIVE', ?, ?)",
                profileId, tenant, keycloakUserId, keycloakUserId + "@example.test", now, now);
        jdbc.update("INSERT INTO app_user_profile_roles (app_user_profile_id, role) VALUES (?, ?)", profileId, role);
        if (vendorId != null) {
            jdbc.update("INSERT INTO merchant_users (id, tenant_id, vendor_id, app_user_profile_id, role) VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID(), tenant, vendorId, profileId, merchantRole);
        }
        return keycloakUserId;
    }

    private String productJson(UUID vendorId) {
        return "{\"vendorId\":\"" + vendorId + "\",\"categoryId\":\"" + categoryId + "\",\"name\":\"Feijão\"}";
    }

    private String orderStatus(UUID orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private String token(String subject, String realmRole, UUID vendorClaim) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        claims.put("azp", "delivery-app");
        claims.put("tenant_id", tenant.toString());
        claims.put("realm_access", Map.of("roles", List.of(realmRole)));
        if (vendorClaim != null) {
            claims.put("vendor_id", vendorClaim.toString());
        }
        String value = "scope-token-" + UUID.randomUUID();
        tokens.put(value, new Jwt(value, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "RS256"), claims));
        return value;
    }

    private static MockHttpServletRequestBuilder withBearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }
}
