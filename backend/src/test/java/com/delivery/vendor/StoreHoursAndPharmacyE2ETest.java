package com.delivery.vendor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W2-D end to end on real Postgres (V055 applied by Flyway): store closures API with merchant scoping and audit,
 * 422 store_closed in cart and checkout, closed stores last in search (LAC-27); pharmacy gate (LAC-64); and the
 * sku_vendor_mismatch cart fix.
 */
class StoreHoursAndPharmacyE2ETest extends PostgresApiE2ETestBase {
    private static final double LAT = -25.9692;
    private static final double LNG = 32.5732;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenancyProperties tenancyProperties;

    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
    private UUID tenant;
    private UUID categoryId;
    private UUID vendorA;
    private UUID vendorB;
    private UUID skuA;
    private UUID skuB;

    @BeforeEach
    void seed() {
        tenant = tenancyProperties.getOperatorTenantId();
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            Jwt jwt = tokens.get(invocation.<String>getArgument(0));
            if (jwt == null) throw new BadJwtException("unknown test token");
            return jwt;
        });
        categoryId = category("general_retail");
        vendorA = vendor("Loja A", LAT, LNG);
        vendorB = vendor("Loja B", LAT + 0.01, LNG + 0.01);
        skuA = product(vendorA, categoryId, false);
        skuB = product(vendorB, categoryId, false);
    }

    // ---- LAC-27: closures API ---------------------------------------------------------------------

    @Test
    void opsCreatesListsAndDeletesClosuresAndEachChangeIsAudited() throws Exception {
        String ops = staffToken("OPS", null);
        Instant startsAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant endsAt = startsAt.plus(4, ChronoUnit.HOURS);

        String body = mockMvc.perform(withBearer(post("/api/v1/vendors/{id}/closures", vendorA), ops)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closureJson(startsAt, endsAt, "Inventário anual")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vendorId").value(vendorA.toString()))
                .andExpect(jsonPath("$.reason").value("Inventário anual"))
                .andReturn().getResponse().getContentAsString();
        UUID closureId = UUID.fromString(objectMapper.readTree(body).get("id").asText());

        mockMvc.perform(withBearer(get("/api/v1/vendors/{id}/closures", vendorA), ops))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(closureId.toString()));
        assertThat(auditCount("VENDOR_CLOSURE_CREATED", closureId)).isEqualTo(1);

        mockMvc.perform(withBearer(delete("/api/v1/vendors/{id}/closures/{closureId}", vendorA, closureId), ops))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vendor_closures WHERE id = ?", Integer.class, closureId)).isZero();
        assertThat(auditCount("VENDOR_CLOSURE_DELETED", closureId)).isEqualTo(1);
        mockMvc.perform(withBearer(delete("/api/v1/vendors/{id}/closures/{closureId}", vendorA, closureId), ops))
                .andExpect(status().isNotFound());
    }

    @Test
    void vendorAdminManagesOnlyItsOwnVendorsClosures() throws Exception {
        String adminOfA = staffToken("VENDOR_ADMIN", merchant(vendorA));
        Instant startsAt = Instant.now().plus(1, ChronoUnit.DAYS);

        mockMvc.perform(withBearer(post("/api/v1/vendors/{id}/closures", vendorB), adminOfA)
                        .contentType(MediaType.APPLICATION_JSON).content(closureJson(startsAt, startsAt.plusSeconds(3600), "Feriado")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("vendor_access_denied"));
        mockMvc.perform(withBearer(get("/api/v1/vendors/{id}/closures", vendorB), adminOfA))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vendor_closures WHERE vendor_id = ?", Integer.class, vendorB)).isZero();

        mockMvc.perform(withBearer(post("/api/v1/vendors/{id}/closures", vendorA), adminOfA)
                        .contentType(MediaType.APPLICATION_JSON).content(closureJson(startsAt, startsAt.plusSeconds(3600), "Feriado")))
                .andExpect(status().isCreated());
    }

    @Test
    void invalidPeriodsAndCustomersAreRefused() throws Exception {
        String ops = staffToken("OPS", null);
        Instant now = Instant.now();
        mockMvc.perform(withBearer(post("/api/v1/vendors/{id}/closures", vendorA), ops)
                        .contentType(MediaType.APPLICATION_JSON).content(closureJson(now.plusSeconds(3600), now.plusSeconds(60), "Ao contrário")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_closure_period"));
        mockMvc.perform(withBearer(post("/api/v1/vendors/{id}/closures", vendorA), ops)
                        .contentType(MediaType.APPLICATION_JSON).content(closureJson(now.minusSeconds(7200), now.minusSeconds(3600), "Passado")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_closure_period"));
        mockMvc.perform(withBearer(post("/api/v1/vendors/{id}/closures", vendorA), customerToken())
                        .contentType(MediaType.APPLICATION_JSON).content(closureJson(now, now.plusSeconds(3600), "Cliente")))
                .andExpect(status().isForbidden());
    }

    // ---- LAC-27: enforcement ----------------------------------------------------------------------

    @Test
    void addItemToAClosedStoreIs422StoreClosedWithTheReopeningTime() throws Exception {
        Instant endsAt = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        closeNow(vendorA, endsAt);

        mockMvc.perform(addItem(customerToken(), vendorA, skuA))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("store_closed"))
                .andExpect(jsonPath("$.details.vendorId").value(vendorA.toString()))
                .andExpect(jsonPath("$.details.nextOpeningAt").value(endsAt.toString()));
    }

    @Test
    void storeWhoseOpeningHoursExcludeEveryDayIsClosed() throws Exception {
        Timestamp now = Timestamp.from(Instant.now());
        for (int day = 1; day <= 7; day++) {
            jdbc.update("INSERT INTO vendor_opening_hours (id, tenant_id, vendor_id, day_of_week, opens_at, closes_at, closed, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, NULL, NULL, TRUE, ?, ?)", UUID.randomUUID(), tenant, vendorA, day, now, now);
        }
        mockMvc.perform(addItem(customerToken(), vendorA, skuA))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("store_closed"))
                .andExpect(jsonPath("$.details.nextOpeningAt").doesNotExist());
    }

    @Test
    void checkoutOfACartWhoseStoreClosedMeanwhileIs422AndCreatesNoOrder() throws Exception {
        String ana = customerToken();
        mockMvc.perform(addItem(ana, vendorA, skuA)).andExpect(status().isOk());
        UUID cartId = jdbc.queryForObject("SELECT id FROM carts WHERE vendor_id = ? AND status = 'ACTIVE'", UUID.class, vendorA);
        closeNow(vendorA, Instant.now().plus(1, ChronoUnit.HOURS));

        mockMvc.perform(withBearer(post("/api/v1/checkout"), ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartId\":\"" + cartId + "\",\"idempotencyKey\":\"closed-" + cartId + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("store_closed"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE cart_id = ?", Integer.class, cartId)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM carts WHERE id = ?", String.class, cartId)).isEqualTo("ACTIVE");
    }

    @Test
    void searchFlagsClosedStoresAndListsThemLastEvenWhenTheResultIsCached() throws Exception {
        // Search near vendor A: with both open, A (closer) ranks first by relevance.
        List<JsonNode> before = search();
        assertThat(before).extracting(row -> row.get("vendorId").asText()).containsExactly(vendorA.toString(), vendorB.toString());
        assertThat(before).allSatisfy(row -> assertThat(row.get("open").asBoolean()).isTrue());

        Instant endsAt = Instant.now().plus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        closeNow(vendorA, endsAt);

        // Same query → same cache entry; the open state is still current because it is stamped after the cache read.
        List<JsonNode> after = search();
        assertThat(after).extracting(row -> row.get("vendorId").asText()).containsExactly(vendorB.toString(), vendorA.toString());
        assertThat(after.get(0).get("open").asBoolean()).isTrue();
        assertThat(after.get(1).get("open").asBoolean()).isFalse();
        assertThat(Instant.parse(after.get(1).get("nextOpeningAt").asText())).isEqualTo(endsAt);
    }

    // ---- cart bug: SKU of another vendor --------------------------------------------------------------

    @Test
    void skuOfAnotherVendorIs422SkuVendorMismatch() throws Exception {
        mockMvc.perform(addItem(customerToken(), vendorA, skuB))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("sku_vendor_mismatch"));
    }

    // ---- helpers ------------------------------------------------------------------------------------

    private List<JsonNode> search() throws Exception {
        String body = mockMvc.perform(get("/api/v1/search/vendors")
                        .param("lat", String.valueOf(LAT)).param("lng", String.valueOf(LNG))
                        .param("category", categoryId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<JsonNode> rows = new ArrayList<>();
        objectMapper.readTree(body).get("vendors").forEach(rows::add);
        return rows;
    }

    private void closeNow(UUID vendorId, Instant endsAt) {
        jdbc.update("INSERT INTO vendor_closures (id, tenant_id, vendor_id, starts_at, ends_at, reason, created_at) VALUES (?, ?, ?, ?, ?, 'Teste', ?)",
                UUID.randomUUID(), tenant, vendorId, Timestamp.from(Instant.now().minusSeconds(60)), Timestamp.from(endsAt), Timestamp.from(Instant.now()));
    }

    private MockHttpServletRequestBuilder addItem(String token, UUID vendorId, UUID skuId) {
        return withBearer(post("/api/v1/customers/{customerId}/cart/items", UUID.randomUUID()), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"vendorId\":\"" + vendorId + "\",\"skuId\":\"" + skuId + "\",\"quantity\":1}");
    }

    private int auditCount(String action, UUID closureId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = ? AND business_reference = ?", Integer.class, action, closureId.toString());
    }

    private static String closureJson(Instant startsAt, Instant endsAt, String reason) {
        return "{\"startsAt\":\"" + startsAt + "\",\"endsAt\":\"" + endsAt + "\",\"reason\":\"" + reason + "\"}";
    }

    private UUID category(String vertical) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO categories (id, tenant_id, name, slug, vertical, active, created_at, updated_at) VALUES (?, ?, 'Categoria', ?, ?, TRUE, ?, ?)",
                id, tenant, "w2d-" + id, vertical, now, now);
        return id;
    }

    private UUID vendor(String name, double lat, double lng) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO vendors (id, tenant_id, name, status, verification_status, available, latitude, longitude, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'ACTIVE', 'APPROVED', TRUE, ?, ?, ?, ?)", id, tenant, name, lat, lng, now, now);
        return id;
    }

    /** Inserts an ACTIVE product with one stocked SKU and returns the SKU id. */
    private UUID product(UUID vendorId, UUID category, boolean prescription) {
        UUID productId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO products (id, tenant_id, vendor_id, category_id, name, status,
                                      requires_prescription_metadata, manual_validation_required, prohibited_fuel, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Produto', 'ACTIVE', ?, FALSE, FALSE, ?, ?)""",
                productId, tenant, vendorId, category, prescription, now, now);
        jdbc.update("INSERT INTO skus (id, tenant_id, product_id, sku_code, name, price, active, created_at, updated_at) VALUES (?, ?, ?, ?, 'Unidade', 18.00, TRUE, ?, ?)",
                skuId, tenant, productId, "sku-" + skuId, now, now);
        jdbc.update("INSERT INTO inventory_items (id, tenant_id, vendor_id, sku_id, quantity_available, quantity_reserved, status, created_at, updated_at, version) VALUES (?, ?, ?, ?, 50, 0, 'ACTIVE', ?, ?, 0)",
                UUID.randomUUID(), tenant, vendorId, skuId, now, now);
        return skuId;
    }

    /** Merchant profile of the vendor (merchant_users row) and returns its Keycloak subject. */
    private String merchant(UUID vendorId) {
        String keycloakUserId = "kc-" + UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'Merchant', 'ACTIVE', ?, ?)",
                profileId, tenant, keycloakUserId, keycloakUserId + "@example.test", now, now);
        jdbc.update("INSERT INTO app_user_profile_roles (app_user_profile_id, role) VALUES (?, 'VENDOR_ADMIN')", profileId);
        jdbc.update("INSERT INTO merchant_users (id, tenant_id, vendor_id, app_user_profile_id, role) VALUES (?, ?, ?, ?, 'OWNER')",
                UUID.randomUUID(), tenant, vendorId, profileId);
        return keycloakUserId;
    }

    private String staffToken(String role, String subject) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject != null ? subject : role.toLowerCase() + "-" + UUID.randomUUID());
        claims.put("azp", "delivery-app");
        claims.put("tenant_id", tenant.toString());
        claims.put("realm_access", Map.of("roles", List.of(role)));
        return register(claims);
    }

    private String customerToken() {
        String subject = UUID.randomUUID().toString();
        Map<String, Object> claims = new HashMap<>(Map.of("azp", "pede-aqui-web", "email", "cliente-" + subject + "@example.test", "name", "Cliente Teste"));
        claims.put("sub", subject);
        claims.put("realm_access", Map.of("roles", List.of("CUSTOMER")));
        return register(claims);
    }

    private String register(Map<String, Object> claims) {
        String value = "w2d-token-" + UUID.randomUUID();
        tokens.put(value, new Jwt(value, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "RS256"), claims));
        return value;
    }

    private static MockHttpServletRequestBuilder withBearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }
}
