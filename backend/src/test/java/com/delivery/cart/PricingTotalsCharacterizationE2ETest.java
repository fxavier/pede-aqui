package com.delivery.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.PostgresApiE2ETestBase;
import com.delivery.common.security.TenancyProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * INC-13 characterization (AS-23, FR-021, FR-063): pins the totals the API produces end to end on real Postgres
 * for one vendor (with coordinates) selling a 18.00 SKU, so every pricing difference introduced by the pricing
 * engine is visible and documented. Scenarios: cart totals, checkout with and without address, a spec-002
 * coupon on cart and checkout, the GET /cart/pricing preview, and the search delivery fee. Tests whose expected
 * values changed with the engine say so (old → new); the engine tests below cover rules and the recorded quote.
 * W6-A (LAC-22): VAT 16 % is now INCLUDED in prices and fees (extracted, never added): every "taxes" line went from
 * 8 % of the subtotal to 0 and the totals dropped by that amount (e.g. 25.34 → 23.90, IVA incluído 3.29).
 */
class PricingTotalsCharacterizationE2ETest extends PostgresApiE2ETestBase {
    private static final double VENDOR_LAT = -25.9692;
    private static final double VENDOR_LNG = 32.5732;
    private static final double CUSTOMER_LAT = -25.9400;
    private static final double CUSTOMER_LNG = 32.5900;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenancyProperties tenancyProperties;

    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
    private UUID tenant;
    private UUID vendorId;
    private UUID categoryId;
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
        skuId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        jdbc.update("INSERT INTO vendors (id, tenant_id, name, status, verification_status, available, latitude, longitude, created_at, updated_at) "
                + "VALUES (?, ?, 'Loja Pricing', 'ACTIVE', 'APPROVED', TRUE, ?, ?, ?, ?)", vendorId, tenant, VENDOR_LAT, VENDOR_LNG, now, now);
        jdbc.update("INSERT INTO categories (id, tenant_id, name, slug, active, created_at, updated_at) VALUES (?, ?, 'Mercearia', ?, TRUE, ?, ?)",
                categoryId, tenant, "pricing-" + categoryId, now, now);
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
    void cartTotalsUseTheFlatDeliveryFeeServiceFeeAndVatIncluded() throws Exception {
        JsonNode cart = addToCart(customerToken());

        // subtotal 18.00 + fees (5.00 delivery + 0.90 service), VAT included = 23.90 (W6-A; was + taxes 1.44 = 25.34)
        assertMoney(cart.get("subtotal"), "18.00");
        assertMoney(cart.get("fees"), "5.90");
        assertMoney(cart.get("taxes"), "0.00");
        assertMoney(cart.get("total"), "23.90");
    }

    @Test
    void legacyCheckoutWithoutAddressChargesTheCartTotals() throws Exception {
        String ana = customerToken();
        UUID cartId = UUID.fromString(addToCart(ana).get("id").asText());

        mockMvc.perform(withBearer(post("/api/v1/checkout"), ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartId\":\"" + cartId + "\",\"idempotencyKey\":\"char-legacy-" + cartId + "\"}"))
                .andExpect(status().isCreated());

        assertOrderTotals(cartId, "5.90", "0", "0", "23.90");
    }

    @Test
    void checkoutWithAnAddressChargesTheCartTotals() throws Exception {
        String ana = customerToken();
        UUID addressId = createAddress(ana, CUSTOMER_LAT, CUSTOMER_LNG);
        UUID cartId = UUID.fromString(addToCart(ana).get("id").asText());

        checkout(ana, cartId, addressId);

        assertOrderTotals(cartId, "5.90", "0", "0", "23.90");
    }

    @Test
    void spec002CouponDiscountsTheCartAndTheCheckout() throws Exception {
        String ana = customerToken();
        String code = "PCT10" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO promotion (id, tenant_id, vendor_id, name, code, type, value, scope, starts_at, ends_at, status, created_at, updated_at)
                VALUES (?, ?, ?, 'Dez por cento', ?, 'PERCENTAGE', 10, 'ORDER', ?, ?, 'ACTIVE', ?, ?)""",
                UUID.randomUUID(), tenant, vendorId, code, Timestamp.from(now.minus(1, ChronoUnit.DAYS)),
                Timestamp.from(now.plus(1, ChronoUnit.DAYS)), Timestamp.from(now), Timestamp.from(now));
        UUID addressId = createAddress(ana, CUSTOMER_LAT, CUSTOMER_LNG);
        UUID cartId = UUID.fromString(addToCart(ana).get("id").asText());

        String pricing = mockMvc.perform(withBearer(post("/api/v1/cart/{cartId}/coupon", cartId), ana)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode cartPricing = objectMapper.readTree(pricing);
        assertMoney(cartPricing.get("fees"), "5.90");
        assertMoney(cartPricing.get("taxes"), "0.00");
        assertMoney(cartPricing.get("discountTotal"), "1.80");
        assertMoney(cartPricing.get("total"), "22.10"); // was 23.54

        checkout(ana, cartId, addressId);

        assertOrderTotals(cartId, "5.90", "0", "1.80", "22.10");
    }

    @Test
    void pricingPreviewEndpointTotals() throws Exception {
        String ana = customerToken();
        String body = mockMvc.perform(withBearer(get("/api/v1/customers/{customerId}/cart/pricing", UUID.randomUUID()), ana)
                        .param("subtotal", "100.00"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode preview = objectMapper.readTree(body);
        assertMoney(preview.get("deliveryFee"), "5.00");
        assertMoney(preview.get("serviceFee"), "5.00");
        assertMoney(preview.get("tax"), "0.00"); // W6-A: VAT included (was 8.00 added)
        assertMoney(preview.get("discount"), "0");
        assertMoney(preview.get("total"), "110.00"); // was 118.00
    }

    @Test
    void pricingPreviewIgnoresTheRemovedMvp10Coupon() throws Exception {
        // INC-13: was discount 10.00 / total 108.00 (magic coupon); now no discount, real coupons use POST /cart/{id}/coupon.
        String ana = customerToken();
        mockMvc.perform(withBearer(get("/api/v1/customers/{customerId}/cart/pricing", UUID.randomUUID()), ana)
                        .param("subtotal", "100.00").param("couponCode", "mvp10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discount").value(0))
                .andExpect(jsonPath("$.total").value(110.0));
    }

    @Test
    void searchShowsTheTenantBaseFeeWhenTheVendorHasNoDistanceRules() throws Exception {
        // INC-13: was 4.33 (double 2.50 + 0.50 * 3.66 km haversine); now the same engine as checkout: no rules → policy base 5.00.
        JsonNode row = searchRow(CUSTOMER_LAT, CUSTOMER_LNG);
        assertThat(row.get("distanceKm").asDouble()).isBetween(3.62, 3.70); // PostGIS geodesic, not haversine
        assertMoney(row.get("deliveryFee"), "5.00");
        assertThat(row.get("deliveryFeeBasis").asText()).isEqualTo("POLICY_BASE");
    }

    // ---- INC-13 engine: rules, quote persistence, search == checkout ----------------------------------

    @Test
    void v048SeedIsCorrectedByV064ToSixteenPercentVatIncluded() {
        Map<String, Object> policy = jdbc.queryForMap("SELECT delivery_fee, service_fee, service_fee_rate, tax_rate, tax_inclusive FROM fee_policies WHERE tenant_id = ?", tenant);
        assertThat((BigDecimal) policy.get("delivery_fee")).isEqualByComparingTo("5.00");
        assertThat((BigDecimal) policy.get("service_fee")).isEqualByComparingTo("0.00");
        assertThat((BigDecimal) policy.get("service_fee_rate")).isEqualByComparingTo("0.05");
        assertThat((BigDecimal) policy.get("tax_rate")).isEqualByComparingTo("0.16"); // V048 seeded 0.08; V064 → 0.16
        assertThat(policy.get("tax_inclusive")).isEqualTo(true);
    }

    @Test
    void checkoutChargesTheVendorDistanceBandShownBySearchAndTheQuoteAndRecordsTheQuote() throws Exception {
        UUID nearRule = UUID.randomUUID();
        UUID farRule = UUID.randomUUID();
        jdbc.update("INSERT INTO delivery_fee_rules (id, tenant_id, vendor_id, min_km, max_km, fee_amount) VALUES (?, ?, ?, 0, 3, 40.00)", nearRule, tenant, vendorId);
        jdbc.update("INSERT INTO delivery_fee_rules (id, tenant_id, vendor_id, min_km, max_km, fee_amount) VALUES (?, ?, ?, 3, 6, 60.00)", farRule, tenant, vendorId);
        String ana = customerToken();
        UUID addressId = createAddress(ana, CUSTOMER_LAT, CUSTOMER_LNG);
        UUID cartId = UUID.fromString(addToCart(ana).get("id").asText());

        JsonNode row = searchRow(CUSTOMER_LAT, CUSTOMER_LNG);
        assertMoney(row.get("deliveryFee"), "60.00");
        assertThat(row.get("deliveryFeeBasis").asText()).isEqualTo("DISTANCE_RULE");

        String quoteBody = mockMvc.perform(withBearer(get("/api/v1/checkout/quote"), ana)
                        .param("cartId", cartId.toString()).param("addressId", addressId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode quote = objectMapper.readTree(quoteBody);
        assertMoney(quote.get("deliveryFee"), "60.00");
        assertMoney(quote.get("serviceFee"), "0.90");
        assertMoney(quote.get("taxes"), "0.00");
        assertMoney(quote.get("total"), "78.90"); // was 80.34
        assertMoney(quote.get("vatIncluded"), "10.88"); // 2.48 goods + 8.28 delivery + 0.12 service
        assertThat(quote.get("deliveryFeeBasis").asText()).isEqualTo("DISTANCE_RULE");

        checkout(ana, cartId, addressId);

        // Search fee == quoted fee == charged delivery fee (ADR-0003 validation criterion).
        assertOrderTotals(cartId, "60.90", "0", "0", "78.90");
        Map<String, Object> recorded = jdbc.queryForMap("SELECT quote_engine_version, quote_delivery_fee, quote_service_fee, quote_tax_rate, "
                + "quote_delivery_fee_basis, quote_distance_km, quote_fee_policy_id, quote_fee_policy_version, quote_delivery_fee_rule_id, "
                + "quote_delivery_fee_rule_version, quoted_at FROM orders WHERE cart_id = ?", cartId);
        assertThat(recorded.get("quote_engine_version")).isEqualTo("pricing-v2");
        assertThat((BigDecimal) recorded.get("quote_delivery_fee")).isEqualByComparingTo(row.get("deliveryFee").decimalValue());
        assertThat((BigDecimal) recorded.get("quote_service_fee")).isEqualByComparingTo("0.90");
        assertThat((BigDecimal) recorded.get("quote_tax_rate")).isEqualByComparingTo("0.16");
        assertThat(recorded.get("quote_delivery_fee_basis")).isEqualTo("DISTANCE_RULE");
        assertThat(((BigDecimal) recorded.get("quote_distance_km")).doubleValue()).isEqualTo(row.get("distanceKm").asDouble());
        assertThat(recorded.get("quote_fee_policy_id")).isEqualTo(jdbc.queryForObject("SELECT id FROM fee_policies WHERE tenant_id = ?", UUID.class, tenant));
        assertThat(recorded.get("quote_fee_policy_version")).isNotNull();
        assertThat(recorded.get("quote_delivery_fee_rule_id")).isEqualTo(farRule);
        assertThat(((Number) recorded.get("quote_delivery_fee_rule_version")).longValue()).isZero();
        assertThat(recorded.get("quoted_at")).isNotNull();
    }

    @Test
    void addressWithoutCoordinatesAndLegacyCheckoutUseThePolicyBaseFee() throws Exception {
        jdbc.update("INSERT INTO delivery_fee_rules (id, tenant_id, vendor_id, min_km, max_km, fee_amount) VALUES (?, ?, ?, 0, 50, 99.00)", UUID.randomUUID(), tenant, vendorId);
        String ana = customerToken();
        UUID noPin = createAddress(ana, null, null);
        UUID cartId = UUID.fromString(addToCart(ana).get("id").asText());
        checkout(ana, cartId, noPin);
        assertOrderTotals(cartId, "5.90", "0", "0", "23.90");
        assertThat(jdbc.queryForObject("SELECT quote_delivery_fee_basis FROM orders WHERE cart_id = ?", String.class, cartId)).isEqualTo("POLICY_BASE");
        assertThat(jdbc.queryForObject("SELECT quote_distance_km FROM orders WHERE cart_id = ?", BigDecimal.class, cartId)).isNull();

        String bruno = customerToken();
        UUID legacyCart = UUID.fromString(addToCart(bruno).get("id").asText());
        mockMvc.perform(withBearer(post("/api/v1/checkout"), bruno).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartId\":\"" + legacyCart + "\",\"idempotencyKey\":\"legacy-q-" + legacyCart + "\"}"))
                .andExpect(status().isCreated());
        assertOrderTotals(legacyCart, "5.90", "0", "0", "23.90");
        assertThat(jdbc.queryForObject("SELECT quote_delivery_fee_basis FROM orders WHERE cart_id = ?", String.class, legacyCart)).isEqualTo("POLICY_BASE");
    }

    @Test
    void theRecordedQuoteIsImmutable() throws Exception {
        String ana = customerToken();
        UUID cartId = UUID.fromString(addToCart(ana).get("id").asText());
        checkout(ana, cartId, createAddress(ana, CUSTOMER_LAT, CUSTOMER_LNG));

        assertThatThrownBy(() -> jdbc.update("UPDATE orders SET quote_delivery_fee = 0 WHERE cart_id = ?", cartId))
                .hasMessageContaining("order pricing quote is immutable");
        // Other order columns still change normally.
        jdbc.update("UPDATE orders SET status = 'PAYMENT_CONFIRMED' WHERE cart_id = ?", cartId);
    }

    @Test
    void adminConfiguredPolicyIsAppliedAndItsVersionRecorded() throws Exception {
        Map<String, Object> original = jdbc.queryForMap("SELECT delivery_fee, service_fee, service_fee_rate, tax_rate, commission_rate, "
                + "cancellation_policy, version FROM fee_policies WHERE tenant_id = ?", tenant);
        try {
            String admin = adminToken();
            String body = "{\"deliveryFee\":7.00,\"serviceFee\":1.00,\"serviceFeeRate\":0.02,\"taxRate\":0.16,"
                    + "\"commissionRate\":0.10,\"cancellationPolicy\":\"Teste\"}";
            String saved = mockMvc.perform(withBearer(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/policy"), admin)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.serviceFeeRate").value(0.02))
                    .andReturn().getResponse().getContentAsString();
            long version = objectMapper.readTree(saved).get("version").asLong();
            assertThat(version).isGreaterThan(((Number) original.get("version")).longValue());
            mockMvc.perform(withBearer(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/policy"), admin)
                            .contentType(MediaType.APPLICATION_JSON).content(body.replace("0.16", "8")))
                    .andExpect(status().isBadRequest());

            String ana = customerToken();
            JsonNode cart = addToCart(ana);
            // 18.00 + 7.00 delivery + (1.00 + 0.36) service, 16 % VAT included = 26.36 (W6-A; was + 2.88 tax = 29.24)
            assertMoney(cart.get("fees"), "8.36");
            assertMoney(cart.get("taxes"), "0.00");
            assertMoney(cart.get("total"), "26.36");
            UUID cartId = UUID.fromString(cart.get("id").asText());
            checkout(ana, cartId, createAddress(ana, CUSTOMER_LAT, CUSTOMER_LNG));
            assertOrderTotals(cartId, "8.36", "0", "0", "26.36");
            assertThat(jdbc.queryForObject("SELECT quote_fee_policy_version FROM orders WHERE cart_id = ?", Long.class, cartId)).isEqualTo(version);
        } finally {
            jdbc.update("UPDATE fee_policies SET delivery_fee = ?, service_fee = ?, service_fee_rate = ?, tax_rate = ?, commission_rate = ?, "
                    + "cancellation_policy = ? WHERE tenant_id = ?", original.get("delivery_fee"), original.get("service_fee"),
                    original.get("service_fee_rate"), original.get("tax_rate"), original.get("commission_rate"), original.get("cancellation_policy"), tenant);
        }
    }

    @Test
    void quoteOfAnotherCustomersCartIsForbidden() throws Exception {
        String ana = customerToken();
        UUID cartId = UUID.fromString(addToCart(ana).get("id").asText());
        mockMvc.perform(withBearer(get("/api/v1/checkout/quote"), customerToken()).param("cartId", cartId.toString()))
                .andExpect(status().isForbidden());
    }

    // ---- helpers -----------------------------------------------------------------------------------

    protected JsonNode searchRow(double lat, double lng) throws Exception {
        String body = mockMvc.perform(get("/api/v1/search/vendors")
                        .param("lat", String.valueOf(lat)).param("lng", String.valueOf(lng))
                        .param("category", categoryId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode vendor : objectMapper.readTree(body).get("vendors")) {
            if (vendorId.toString().equals(vendor.get("vendorId").asText())) {
                return vendor;
            }
        }
        throw new AssertionError("vendor not found in search results: " + body);
    }

    private void assertOrderTotals(UUID cartId, String fees, String taxes, String discountTotal, String total) {
        Map<String, Object> order = jdbc.queryForMap("SELECT o.subtotal, o.fees, o.taxes, o.discount_total, o.total, p.amount "
                + "FROM orders o JOIN payments p ON p.order_id = o.id WHERE o.cart_id = ?", cartId);
        assertThat((BigDecimal) order.get("subtotal")).isEqualByComparingTo("18.00");
        assertThat((BigDecimal) order.get("fees")).isEqualByComparingTo(fees);
        assertThat((BigDecimal) order.get("taxes")).isEqualByComparingTo(taxes);
        assertThat((BigDecimal) order.get("discount_total")).isEqualByComparingTo(discountTotal);
        assertThat((BigDecimal) order.get("total")).isEqualByComparingTo(total);
        assertThat((BigDecimal) order.get("amount")).isEqualByComparingTo(total);
    }

    private static void assertMoney(JsonNode node, String expected) {
        assertThat(node).as("money field").isNotNull();
        assertThat(node.decimalValue()).isEqualByComparingTo(expected);
    }

    private void checkout(String token, UUID cartId, UUID addressId) throws Exception {
        mockMvc.perform(withBearer(post("/api/v1/checkout"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartId\":\"" + cartId + "\",\"idempotencyKey\":\"char-" + cartId + "\",\"addressId\":\"" + addressId + "\"}"))
                .andExpect(status().isCreated());
    }

    private JsonNode addToCart(String token) throws Exception {
        String cart = mockMvc.perform(withBearer(post("/api/v1/customers/{customerId}/cart/items", UUID.randomUUID()), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vendorId\":\"" + vendorId + "\",\"skuId\":\"" + skuId + "\",\"quantity\":1}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cart);
    }

    private UUID createAddress(String token, Double latitude, Double longitude) throws Exception {
        Map<String, Object> address = new HashMap<>();
        address.put("label", "Casa");
        address.put("recipientName", "Ana Teste");
        address.put("phone", "+258840000000");
        address.put("line1", "Av. Julius Nyerere 1200");
        address.put("city", "Maputo");
        address.put("latitude", latitude);
        address.put("longitude", longitude);
        String body = mockMvc.perform(withBearer(post("/api/v1/customers/me/addresses"), token).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(address)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private String customerToken() {
        String subject = UUID.randomUUID().toString();
        Map<String, Object> claims = new HashMap<>(Map.of("azp", "pede-aqui-web", "email", "cliente-" + subject + "@example.test", "name", "Cliente Teste"));
        claims.put("sub", subject);
        claims.put("realm_access", Map.of("roles", List.of("CUSTOMER")));
        String value = "test-token-" + UUID.randomUUID();
        tokens.put(value, new Jwt(value, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "RS256"), claims));
        return value;
    }

    private String adminToken() {
        Map<String, Object> claims = new HashMap<>(Map.of("azp", "delivery-app", "tenant_id", tenant.toString()));
        claims.put("sub", "admin-" + UUID.randomUUID());
        claims.put("realm_access", Map.of("roles", List.of("ADMIN")));
        String value = "test-token-" + UUID.randomUUID();
        tokens.put(value, new Jwt(value, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "RS256"), claims));
        return value;
    }

    private static MockHttpServletRequestBuilder withBearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }
}
