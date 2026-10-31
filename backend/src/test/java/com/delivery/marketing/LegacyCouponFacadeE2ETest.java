package com.delivery.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * LAC-29: {@code /api/v1/marketing/coupons} contract as the backoffice /marketing page uses it (create, list,
 * deactivate), pinned before the endpoint became a façade over the spec-002 {@code promotion} table and kept
 * identical after: same JSON members, status codes and error codes. The second group of tests covers what the
 * façade adds: coupons are promotions (redeemable, same discount as a spec-002 coupon) and V073-migrated legacy
 * coupons keep their id and shape.
 */
class LegacyCouponFacadeE2ETest extends PostgresApiE2ETestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenancyProperties tenancyProperties;

    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
    private UUID tenant;
    private UUID vendorId;
    private String ops;
    private UUID skuId;

    @BeforeEach
    void seed() {
        tenant = tenancyProperties.getOperatorTenantId();
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            Jwt jwt = tokens.get(invocation.<String>getArgument(0));
            if (jwt == null) throw new BadJwtException("unknown test token");
            return jwt;
        });
        Timestamp now = Timestamp.from(Instant.now());
        vendorId = UUID.randomUUID();
        jdbc.update("INSERT INTO vendors (id, tenant_id, name, status, verification_status, available, created_at, updated_at) "
                + "VALUES (?, ?, 'Loja Cupao', 'ACTIVE', 'APPROVED', TRUE, ?, ?)", vendorId, tenant, now, now);
        ops = staffToken("OPS");
        skuId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, tenant_id, name, slug, active, created_at, updated_at) VALUES (?, ?, 'Mercearia', ?, TRUE, ?, ?)",
                categoryId, tenant, "coupon-" + categoryId, now, now);
        jdbc.update("""
                INSERT INTO products (id, tenant_id, vendor_id, category_id, name, status,
                                      requires_prescription_metadata, manual_validation_required, prohibited_fuel, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Arroz', 'ACTIVE', FALSE, FALSE, FALSE, ?, ?)""",
                productId, tenant, vendorId, categoryId, now, now);
        jdbc.update("INSERT INTO skus (id, tenant_id, product_id, sku_code, name, price, active, created_at, updated_at) VALUES (?, ?, ?, ?, 'Arroz 1kg', 18.00, TRUE, ?, ?)",
                skuId, tenant, productId, "sku-" + skuId, now, now);
        jdbc.update("INSERT INTO inventory_items (id, tenant_id, vendor_id, sku_id, quantity_available, quantity_reserved, status, created_at, updated_at, version) VALUES (?, ?, ?, ?, 50, 0, 'ACTIVE', ?, ?, 0)",
                UUID.randomUUID(), tenant, vendorId, skuId, now, now);
    }

    @Test
    void createReturnsTheCouponShapeWithUppercasedCode() throws Exception {
        String code = "lgc" + suffix();
        mockMvc.perform(withBearer(post("/api/v1/marketing/coupons"), ops).contentType(MediaType.APPLICATION_JSON)
                        .content(couponJson(code, "PERCENTAGE", "15", "100.00", 50, vendorId,
                                "2026-01-01T00:00:00Z", "2099-06-30T23:59:59Z")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.code").value(code.toUpperCase()))
                .andExpect(jsonPath("$.discountType").value("PERCENTAGE"))
                .andExpect(jsonPath("$.discountValue").value(15))
                .andExpect(jsonPath("$.minOrderAmount").value(100.0))
                .andExpect(jsonPath("$.maxUses").value(50))
                .andExpect(jsonPath("$.usesCount").value(0))
                .andExpect(jsonPath("$.vendorId").value(vendorId.toString()))
                .andExpect(jsonPath("$.validFrom").value("2026-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.validUntil").value("2099-06-30T23:59:59Z"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void openEndedCouponKeepsANullValidUntil() throws Exception {
        String code = "OPEN" + suffix();
        mockMvc.perform(withBearer(post("/api/v1/marketing/coupons"), ops).contentType(MediaType.APPLICATION_JSON)
                        .content(couponJson(code, "FIXED_AMOUNT", "20", null, null, null, "2026-01-01T00:00:00Z", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validUntil").isEmpty())
                .andExpect(jsonPath("$.vendorId").isEmpty())
                .andExpect(jsonPath("$.maxUses").isEmpty())
                .andExpect(jsonPath("$.minOrderAmount").isEmpty());
    }

    @Test
    void duplicateCodeIsAConflict() throws Exception {
        String code = "DUP" + suffix();
        create(code);
        mockMvc.perform(withBearer(post("/api/v1/marketing/coupons"), ops).contentType(MediaType.APPLICATION_JSON)
                        .content(couponJson(code.toLowerCase(), "PERCENTAGE", "10", null, null, vendorId, "2026-01-01T00:00:00Z", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate_coupon_code"));
    }

    @Test
    void listContainsCreatedCouponsAndDeactivateIsOneWay() throws Exception {
        String code = "LST" + suffix();
        String id = create(code).get("id").asText();

        JsonNode list = objectMapper.readTree(mockMvc.perform(withBearer(get("/api/v1/marketing/coupons"), ops))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(list.isArray()).isTrue();
        assertThat(list.findValuesAsText("code")).contains(code);

        mockMvc.perform(withBearer(patch("/api/v1/marketing/coupons/{id}/deactivate", id), ops))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(withBearer(patch("/api/v1/marketing/coupons/{id}/deactivate", id), ops))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("already_inactive"));
        mockMvc.perform(withBearer(patch("/api/v1/marketing/coupons/{id}/deactivate", UUID.randomUUID()), ops))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminAndOpsMayUseTheEndpoint() throws Exception {
        mockMvc.perform(withBearer(get("/api/v1/marketing/coupons"), staffToken("ADMIN"))).andExpect(status().isOk());
        mockMvc.perform(withBearer(get("/api/v1/marketing/coupons"), staffToken("VENDOR_ADMIN"))).andExpect(status().isForbidden());
        mockMvc.perform(withBearer(get("/api/v1/marketing/coupons"), staffToken("FINANCE"))).andExpect(status().isForbidden());
    }

    @Test
    void missingRequiredFieldsAreValidationErrors() throws Exception {
        mockMvc.perform(withBearer(post("/api/v1/marketing/coupons"), ops).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"discountType\":\"PERCENTAGE\",\"discountValue\":10}"))
                .andExpect(status().isBadRequest());
    }

    // --- LAC-29 façade behaviour (added with the consolidation) ---

    @Test
    void facadeCouponIsAPromotionRedeemableWithTheSameDiscountAsASpec002Coupon() throws Exception {
        String code = "FAC" + suffix();
        String id = create(code).get("id").asText();

        // Written to promotion, never to the legacy table.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM coupons WHERE code = ?", Integer.class, code)).isZero();
        Map<String, Object> row = jdbc.queryForMap("SELECT code, type, scope, status, vendor_id FROM promotion WHERE id = ?::uuid", id);
        assertThat(row).containsEntry("code", code).containsEntry("type", "PERCENTAGE").containsEntry("scope", "ORDER")
                .containsEntry("status", "ACTIVE").containsEntry("vendor_id", vendorId);
        // Visible to the spec-002 promotions API too (one model).
        JsonNode promotions = objectMapper.readTree(mockMvc.perform(withBearer(get("/api/v1/marketing/promotions"), ops))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(promotions.findValuesAsText("code")).contains(code);

        // Same totals as PromotionDiscountCharacterizationE2ETest / PricingTotalsCharacterizationE2ETest for 10 %.
        String ana = customerToken();
        UUID cartId = cartId(ana);
        mockMvc.perform(withBearer(post("/api/v1/cart/{cartId}/coupon", cartId), ana)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountTotal").value(1.80))
                .andExpect(jsonPath("$.total").value(22.10));
    }

    @Test
    void deactivatedCouponIsNoLongerRedeemable() throws Exception {
        String code = "OFF" + suffix();
        String id = create(code).get("id").asText();
        mockMvc.perform(withBearer(patch("/api/v1/marketing/coupons/{id}/deactivate", id), ops)).andExpect(status().isOk());

        String ana = customerToken();
        mockMvc.perform(withBearer(post("/api/v1/cart/{cartId}/coupon", cartId(ana)), ana)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("coupon_not_active"));
        assertThat(jdbc.queryForObject("SELECT status FROM promotion WHERE id = ?::uuid", String.class, id)).isEqualTo("PAUSED");
    }

    @Test
    void unknownDiscountTypeIsRejected() throws Exception {
        mockMvc.perform(withBearer(post("/api/v1/marketing/coupons"), ops).contentType(MediaType.APPLICATION_JSON)
                        .content(couponJson("BOGO" + suffix(), "BUY_ONE_GET_ONE", "10", null, null, vendorId, "2026-01-01T00:00:00Z", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_discount_type"));
    }

    @Test
    void migratedLegacyCouponKeepsItsIdAndShapeAndIsRedeemable() throws Exception {
        UUID legacyId = UUID.randomUUID();
        String code = "LEG" + suffix();
        Timestamp created = Timestamp.from(Instant.parse("2026-09-01T10:00:00Z"));
        jdbc.update("""
                INSERT INTO coupons (id, tenant_id, code, discount_type, discount_value, min_order_amount, max_uses, uses_count,
                                     vendor_id, valid_from, valid_until, active, created_at, updated_at)
                VALUES (?, ?, ?, 'PERCENT', 10, NULL, 500, 3, ?, ?, NULL, TRUE, ?, ?)""",
                legacyId, tenant, code, vendorId, Timestamp.from(Instant.parse("2026-09-01T00:00:00Z")), created, created);

        List<Map<String, Object>> report = jdbc.queryForList("SELECT * FROM marketing_migrate_legacy_promotions()");
        assertThat(report).anySatisfy(r -> assertThat(r).containsEntry("legacy_table", "coupons").containsEntry("outcome", "MIGRATED"));
        // Idempotent: a second run migrates nothing new and the legacy row is kept, only marked.
        assertThat(jdbc.queryForList("SELECT * FROM marketing_migrate_legacy_promotions()")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT migrated_to_promotion_id FROM coupons WHERE id = ?", UUID.class, legacyId)).isEqualTo(legacyId);

        JsonNode list = objectMapper.readTree(mockMvc.perform(withBearer(get("/api/v1/marketing/coupons"), ops))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode migrated = null;
        for (JsonNode c : list) {
            if (c.get("id").asText().equals(legacyId.toString())) migrated = c;
        }
        assertThat(migrated).as("migrated coupon listed under its legacy id").isNotNull();
        assertThat(migrated.get("code").asText()).isEqualTo(code);
        assertThat(migrated.get("discountType").asText()).isEqualTo("PERCENTAGE");
        assertThat(migrated.get("maxUses").asInt()).isEqualTo(500);
        assertThat(migrated.get("usesCount").asInt()).isEqualTo(3);
        assertThat(migrated.get("validUntil").isNull()).isTrue();
        // Owner-safe default (V073): migrated PAUSED even though the legacy row was active — no behaviour change for
        // customers until OPS/ADMIN reviews and activates it.
        assertThat(migrated.get("active").asBoolean()).isFalse();
        assertThat(migrated.get("createdAt").asText()).isEqualTo("2026-09-01T10:00:00Z");
        assertThat(jdbc.queryForObject("SELECT reason FROM legacy_promotion_migration WHERE legacy_table = 'coupons' AND legacy_id = ?",
                String.class, legacyId)).isEqualTo("paused_for_review");

        String ana = customerToken();
        UUID anaCart = cartId(ana);
        mockMvc.perform(withBearer(post("/api/v1/cart/{cartId}/coupon", anaCart), ana)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code.toLowerCase() + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("coupon_not_active"));

        mockMvc.perform(withBearer(post("/api/v1/marketing/promotions/{id}/activate", legacyId), ops))
                .andExpect(status().isOk());
        mockMvc.perform(withBearer(post("/api/v1/cart/{cartId}/coupon", anaCart), ana)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code.toLowerCase() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountTotal").value(1.80));
    }

    // --- helpers ---

    private UUID cartId(String token) throws Exception {
        String cart = mockMvc.perform(withBearer(post("/api/v1/customers/{customerId}/cart/items", UUID.randomUUID()), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vendorId\":\"" + vendorId + "\",\"skuId\":\"" + skuId + "\",\"quantity\":1}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(cart).get("id").asText());
    }

    private String customerToken() {
        String subject = UUID.randomUUID().toString();
        Map<String, Object> claims = new HashMap<>(Map.of("azp", "pede-aqui-web", "email", "cupao-" + subject + "@example.test", "name", "Cliente Cupao"));
        claims.put("sub", subject);
        claims.put("realm_access", Map.of("roles", List.of("CUSTOMER")));
        String value = "test-token-" + UUID.randomUUID();
        tokens.put(value, new Jwt(value, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "RS256"), claims));
        return value;
    }

    private JsonNode create(String code) throws Exception {
        String body = mockMvc.perform(withBearer(post("/api/v1/marketing/coupons"), ops).contentType(MediaType.APPLICATION_JSON)
                        .content(couponJson(code, "PERCENTAGE", "10", null, null, vendorId, "2026-01-01T00:00:00Z", null)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private String couponJson(String code, String type, String value, String minOrder, Integer maxUses, UUID vendor,
                              String validFrom, String validUntil) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("discountType", type);
        body.put("discountValue", new java.math.BigDecimal(value));
        if (minOrder != null) body.put("minOrderAmount", new java.math.BigDecimal(minOrder));
        if (maxUses != null) body.put("maxUses", maxUses);
        if (vendor != null) body.put("vendorId", vendor.toString());
        body.put("validFrom", validFrom);
        if (validUntil != null) body.put("validUntil", validUntil);
        return objectMapper.writeValueAsString(body);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String staffToken(String role) {
        Map<String, Object> claims = new HashMap<>(Map.of("azp", "delivery-app", "tenant_id", tenant.toString()));
        claims.put("sub", role.toLowerCase() + "-" + UUID.randomUUID());
        claims.put("realm_access", Map.of("roles", List.of(role)));
        String value = "test-token-" + UUID.randomUUID();
        tokens.put(value, new Jwt(value, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "RS256"), claims));
        return value;
    }

    private static MockHttpServletRequestBuilder withBearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }
}
