package com.delivery.vendor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.PostgresApiE2ETestBase;
import com.delivery.common.TestParents;
import com.delivery.common.security.TenancyProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
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
 * LAC-16 acceptance (W3-D) on the full context with real Postgres: two merchants, each a vendor of the operator
 * tenant with its VENDOR_ADMIN linked through merchant_users. Neither can see or act on the other's orders,
 * products, stock, closures, sales or reports; OPS of the operator sees both. Also covers SKU codes being unique
 * per vendor (V056) and the capped closures list.
 */
class TwoMerchantIsolationE2ETest extends PostgresApiE2ETestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenancyProperties tenancyProperties;

    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
    private UUID tenant;
    private UUID categoryId;
    private Merchant a;
    private Merchant b;
    private String ops;

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
                categoryId, tenant, "two-merchants-" + categoryId, now, now);
        a = merchant("Loja A", now);
        b = merchant("Loja B", now);
        ops = token("kc-ops-" + UUID.randomUUID(), "OPS");
    }

    @Test
    void ordersAreConfinedToTheOwnVendorInEveryListingAndAction() throws Exception {
        for (Merchant self : List.of(a, b)) {
            Merchant other = self == a ? b : a;
            assertThat(ids(arrayBody(get("/api/v1/orders"), self.token), "id")).contains(self.order).doesNotContain(other.order);
            assertThat(ids(body(get("/api/v1/orders/cursor").param("limit", "200"), self.token).get("items"), "id"))
                    .contains(self.order).doesNotContain(other.order);
            assertThat(ids(body(get("/api/v1/orders/page").param("size", "100"), self.token).get("content"), "id"))
                    .contains(self.order).doesNotContain(other.order);

            mockMvc.perform(withBearer(patch("/api/v1/vendor/orders/{id}/accept", other.order), self.token))
                    .andExpect(status().isNotFound());
            assertThat(orderStatus(other.order)).isEqualTo("PAYMENT_CONFIRMED");
        }
    }

    @Test
    void productsAndStockOfTheOtherMerchantAreOffLimits() throws Exception {
        mockMvc.perform(withBearer(patch("/api/v1/catalog/products/{id}", b.product), a.token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Roubado\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("vendor_access_denied"));
        assertThat(jdbc.queryForObject("SELECT name FROM products WHERE id = ?", String.class, b.product)).isEqualTo("Arroz");

        mockMvc.perform(withBearer(post("/api/v1/catalog/skus"), a.token)
                        .contentType(MediaType.APPLICATION_JSON).content(skuJson(b, "INTRUSO")))
                .andExpect(status().isForbidden());

        mockMvc.perform(withBearer(patch("/api/v1/inventory/{id}/stock", b.inventory), a.token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantityAvailable\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("vendor_access_denied"));
        assertThat(jdbc.queryForObject("SELECT quantity_available FROM inventory_items WHERE id = ?", Integer.class, b.inventory)).isEqualTo(50);

        mockMvc.perform(withBearer(patch("/api/v1/inventory/{id}/stock", a.inventory), a.token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantityAvailable\":9}"))
                .andExpect(status().isOk());
    }

    @Test
    void closuresOfTheOtherMerchantCanNeitherBeReadNorChanged() throws Exception {
        UUID closureB = closure(b.vendor, Instant.now().plus(Duration.ofDays(1)));

        mockMvc.perform(withBearer(get("/api/v1/vendors/{v}/closures", b.vendor), a.token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("vendor_access_denied"));
        mockMvc.perform(withBearer(post("/api/v1/vendors/{v}/closures", b.vendor), a.token)
                        .contentType(MediaType.APPLICATION_JSON).content(closureJson()))
                .andExpect(status().isForbidden());
        mockMvc.perform(withBearer(delete("/api/v1/vendors/{v}/closures/{c}", b.vendor, closureB), a.token))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vendor_closures WHERE id = ?", Integer.class, closureB)).isEqualTo(1);

        mockMvc.perform(withBearer(post("/api/v1/vendors/{v}/closures", a.vendor), a.token)
                        .contentType(MediaType.APPLICATION_JSON).content(closureJson()))
                .andExpect(status().isCreated());
        mockMvc.perform(withBearer(get("/api/v1/vendors/{v}/closures", a.vendor), a.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(header().doesNotExist("X-Truncated"));
    }

    @Test
    void salesAndReportsNeverExposeTheOtherMerchant() throws Exception {
        for (Merchant self : List.of(a, b)) {
            Merchant other = self == a ? b : a;
            mockMvc.perform(withBearer(get("/api/v1/sales/orders/{id}", other.order), self.token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("vendor_access_denied"));
            mockMvc.perform(withBearer(get("/api/v1/sales/orders/{id}", self.order), self.token))
                    .andExpect(status().isOk());
            assertThat(ids(body(get("/api/v1/sales/orders").param("size", "100"), self.token).get("content"), "orderId"))
                    .containsExactly(self.order);

            List<String> vendorKeys = keys(arrayBody(get("/api/v1/reports/sales/by-vendor")
                    .param("from", "2026-01-01T00:00:00Z").param("to", "2099-01-01T00:00:00Z"), self.token));
            assertThat(vendorKeys).contains(self.vendor.toString()).doesNotContain(other.vendor.toString());
        }
    }

    @Test
    void opsOfTheOperatorSeesBothMerchants() throws Exception {
        assertThat(ids(arrayBody(get("/api/v1/orders"), ops), "id")).contains(a.order, b.order);
        assertThat(ids(body(get("/api/v1/orders/cursor").param("limit", "200"), ops).get("items"), "id")).contains(a.order, b.order);
        for (Merchant m : List.of(a, b)) {
            mockMvc.perform(withBearer(get("/api/v1/sales/orders/{id}", m.order), ops)).andExpect(status().isOk());
            mockMvc.perform(withBearer(get("/api/v1/vendors/{v}/closures", m.vendor), ops)).andExpect(status().isOk());
            mockMvc.perform(withBearer(patch("/api/v1/inventory/{id}/stock", m.inventory), ops)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"quantityAvailable\":11}"))
                    .andExpect(status().isOk());
        }
        List<String> vendorKeys = keys(arrayBody(get("/api/v1/reports/sales/by-vendor")
                .param("from", "2026-01-01T00:00:00Z").param("to", "2099-01-01T00:00:00Z"), ops));
        assertThat(vendorKeys).contains(a.vendor.toString(), b.vendor.toString());
    }

    @Test
    void skuCodeIsUniquePerVendorNotPerTenant() throws Exception {
        String code = "ARROZ-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(withBearer(post("/api/v1/catalog/skus"), a.token)
                        .contentType(MediaType.APPLICATION_JSON).content(skuJson(a, code)))
                .andExpect(status().isCreated());
        mockMvc.perform(withBearer(post("/api/v1/catalog/skus"), b.token)
                        .contentType(MediaType.APPLICATION_JSON).content(skuJson(b, code)))
                .andExpect(status().isCreated());
        mockMvc.perform(withBearer(post("/api/v1/catalog/skus"), ops)
                        .contentType(MediaType.APPLICATION_JSON).content(skuJson(a, code)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("sku_code_conflict"));

        // vendor_id is derived by the V056 trigger from the product, never from the writer.
        assertThat(jdbc.queryForList("SELECT vendor_id FROM skus WHERE tenant_id = ? AND sku_code = ?", UUID.class, tenant, code))
                .containsExactlyInAnyOrder(a.vendor, b.vendor);
        UUID raw = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO skus (id, tenant_id, product_id, vendor_id, sku_code, name, price, active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'x', 1, TRUE, ?, ?)",
                raw, tenant, b.product, a.vendor, "RAW-" + raw, now, now);
        assertThat(jdbc.queryForObject("SELECT vendor_id FROM skus WHERE id = ?", UUID.class, raw)).isEqualTo(b.vendor);
    }

    @Test
    void closureListIsCappedWithTruncationHeader() throws Exception {
        Instant start = Instant.now().plus(Duration.ofDays(1));
        for (int i = 0; i < 201; i++) {
            closure(a.vendor, start.plus(Duration.ofMinutes(i)));
        }
        mockMvc.perform(withBearer(get("/api/v1/vendors/{v}/closures", a.vendor), ops))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Truncated", "true"))
                .andExpect(jsonPath("$.length()").value(200));
    }

    // --- helpers ---

    private record Merchant(UUID vendor, UUID product, UUID sku, UUID inventory, UUID order, String token) {}

    private Merchant merchant(String name, Timestamp now) {
        UUID vendorId = UUID.randomUUID();
        jdbc.update("INSERT INTO vendors (id, tenant_id, name, status, verification_status, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', 'APPROVED', ?, ?)",
                vendorId, tenant, name, now, now);
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
        UUID orderId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, status, subtotal, fees, taxes, discounts,
                                    discount_total, total, checkout_idempotency_key, delivery_confirmation_code_hash,
                                    delivery_confirmation_code_display, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'PAYMENT_CONFIRMED', 90, 10, 0, 0, 0, 100, ?, 'hash', '000000', ?, ?)""",
                orderId, tenant, "ref-" + orderId, TestParents.customer(jdbc, tenant), vendorId, "key-" + orderId, now, now);

        String keycloakUserId = "kc-" + UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'Merchant', 'ACTIVE', ?, ?)",
                profileId, tenant, keycloakUserId, keycloakUserId + "@example.test", now, now);
        jdbc.update("INSERT INTO app_user_profile_roles (app_user_profile_id, role) VALUES (?, 'VENDOR_ADMIN')", profileId);
        jdbc.update("INSERT INTO merchant_users (id, tenant_id, vendor_id, app_user_profile_id, role) VALUES (?, ?, ?, ?, 'OWNER')",
                UUID.randomUUID(), tenant, vendorId, profileId);
        return new Merchant(vendorId, productId, skuId, itemId, orderId, token(keycloakUserId, "VENDOR_ADMIN"));
    }

    private UUID closure(UUID vendorId, Instant startsAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO vendor_closures (id, tenant_id, vendor_id, starts_at, ends_at, reason, created_at) VALUES (?, ?, ?, ?, ?, 'Inventário', ?)",
                id, tenant, vendorId, Timestamp.from(startsAt), Timestamp.from(startsAt.plus(Duration.ofHours(2))), Timestamp.from(Instant.now()));
        return id;
    }

    private String closureJson() {
        Instant start = Instant.now().plus(Duration.ofDays(3));
        return "{\"startsAt\":\"" + start + "\",\"endsAt\":\"" + start.plus(Duration.ofHours(4)) + "\",\"reason\":\"Inventário\"}";
    }

    private String skuJson(Merchant m, String code) {
        return "{\"productId\":\"" + m.product + "\",\"vendorId\":\"" + m.vendor + "\",\"skuCode\":\"" + code
                + "\",\"name\":\"1kg\",\"price\":10,\"initialStock\":1}";
    }

    private JsonNode body(MockHttpServletRequestBuilder request, String token) throws Exception {
        String json = mockMvc.perform(withBearer(request, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private JsonNode arrayBody(MockHttpServletRequestBuilder request, String token) throws Exception {
        JsonNode node = body(request, token);
        assertThat(node.isArray()).isTrue();
        return node;
    }

    private static List<UUID> ids(JsonNode rows, String field) {
        List<UUID> ids = new ArrayList<>();
        rows.forEach(row -> ids.add(UUID.fromString(row.get(field).asText())));
        return ids;
    }

    private static List<String> keys(JsonNode rows) {
        List<String> keys = new ArrayList<>();
        rows.forEach(row -> keys.add(row.get("key").asText()));
        return keys;
    }

    private String orderStatus(UUID orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private String token(String subject, String realmRole) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        claims.put("azp", "delivery-app");
        claims.put("tenant_id", tenant.toString());
        claims.put("realm_access", Map.of("roles", List.of(realmRole)));
        String value = "two-merchants-token-" + UUID.randomUUID();
        tokens.put(value, new Jwt(value, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "RS256"), claims));
        return value;
    }

    private static MockHttpServletRequestBuilder withBearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }
}
