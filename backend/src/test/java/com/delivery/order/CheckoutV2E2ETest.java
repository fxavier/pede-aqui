package com.delivery.order;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.Duration;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W6-A checkout v2 end to end on real Postgres/PostGIS: quote with TTL (quoteId/expiresAt), checkout with quoteId
 * (expired / fees changed / price changed → 409 quote_changed with a fresh quote), VAT extracted and persisted on the
 * order and its lines (16 % included, tax categories), the vendor service area (422 out_of_service_area; zones without
 * polygon allowed) and the maximum delivery distance (422 address_too_far, vendor and tenant policy).
 */
class CheckoutV2E2ETest extends PostgresApiE2ETestBase {
    private static final double VENDOR_LAT = -25.9692;
    private static final double VENDOR_LNG = 32.5732;
    private static final double CUSTOMER_LAT = -25.9400;
    private static final double CUSTOMER_LNG = 32.5900;
    private static final String COVERING_WKT = "POLYGON((32.50 -26.00, 32.70 -26.00, 32.70 -25.90, 32.50 -25.90, 32.50 -26.00))";
    private static final String ELSEWHERE_WKT = "POLYGON((33.00 -25.00, 33.10 -25.00, 33.10 -24.90, 33.00 -24.90, 33.00 -25.00))";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenancyProperties tenancyProperties;

    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
    private UUID tenant;
    private UUID vendorId;
    private UUID productId;
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
        productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        jdbc.update("INSERT INTO vendors (id, tenant_id, name, status, verification_status, available, latitude, longitude, created_at, updated_at) "
                + "VALUES (?, ?, 'Loja W6A', 'ACTIVE', 'APPROVED', TRUE, ?, ?, ?, ?)", vendorId, tenant, VENDOR_LAT, VENDOR_LNG, now, now);
        jdbc.update("INSERT INTO categories (id, tenant_id, name, slug, active, created_at, updated_at) VALUES (?, ?, 'Mercearia', ?, TRUE, ?, ?)",
                categoryId, tenant, "w6a-" + categoryId, now, now);
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

    // ---- quote with TTL + VAT ------------------------------------------------------------------------

    @Test
    void quoteHasAnIdAndExpiryAndShowsTheVatIncludedAndCheckoutWithItRecordsTheVatBreakdown() throws Exception {
        String ana = customerToken();
        UUID addressId = createAddress(ana, CUSTOMER_LAT, CUSTOMER_LNG);
        UUID cartId = cartWithOneItem(ana);

        JsonNode quote = quote(ana, cartId, addressId);
        assertMoney(quote.get("total"), "23.90");
        assertMoney(quote.get("taxes"), "0.00");
        assertThat(quote.get("taxInclusive").asBoolean()).isTrue();
        assertMoney(quote.get("vatRate"), "0.16");
        assertMoney(quote.get("vatIncluded"), "3.29");
        assertMoney(quote.get("goodsVat"), "2.48");
        assertMoney(quote.get("feesVat"), "0.81");
        assertMoney(quote.get("netTotal"), "20.61");
        UUID quoteId = UUID.fromString(quote.get("quoteId").asText());
        Duration ttl = Duration.between(Instant.now(), Instant.parse(quote.get("expiresAt").asText()));
        assertThat(ttl).isBetween(Duration.ofMinutes(9), Duration.ofMinutes(10).plusSeconds(5));

        checkout(ana, cartId, addressId, quoteId).andExpect(status().isCreated());

        Map<String, Object> order = jdbc.queryForMap("SELECT id, total, taxes, quote_id, quote_tax_inclusive, quote_vat_total, quote_net_total, "
                + "quote_goods_vat, quote_fees_vat, quote_goods_standard_gross FROM orders WHERE cart_id = ?", cartId);
        assertThat((BigDecimal) order.get("total")).isEqualByComparingTo("23.90");
        assertThat((BigDecimal) order.get("taxes")).isEqualByComparingTo("0");
        assertThat(order.get("quote_id")).isEqualTo(quoteId);
        assertThat(order.get("quote_tax_inclusive")).isEqualTo(true);
        assertThat((BigDecimal) order.get("quote_vat_total")).isEqualByComparingTo("3.29");
        assertThat((BigDecimal) order.get("quote_net_total")).isEqualByComparingTo("20.61");
        assertThat((BigDecimal) order.get("quote_goods_vat")).isEqualByComparingTo("2.48");
        assertThat((BigDecimal) order.get("quote_fees_vat")).isEqualByComparingTo("0.81");
        assertThat((BigDecimal) order.get("quote_goods_standard_gross")).isEqualByComparingTo("18.00");
        Map<String, Object> line = jdbc.queryForMap("SELECT tax_category, vat_rate, vat_amount FROM order_items WHERE order_id = ?", order.get("id"));
        assertThat(line.get("tax_category")).isEqualTo("STANDARD");
        assertThat((BigDecimal) line.get("vat_rate")).isEqualByComparingTo("0.16");
        assertThat((BigDecimal) line.get("vat_amount")).isEqualByComparingTo("2.48");

        // The customer's order detail exposes the VAT included.
        mockMvc.perform(withBearer(get("/api/v1/me/orders/{id}", order.get("id")), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxInclusive").value(true))
                .andExpect(jsonPath("$.vatIncluded").value(3.29));
    }

    @Test
    void exemptProductsCarryNoVat() throws Exception {
        jdbc.update("UPDATE products SET tax_category = 'EXEMPT' WHERE id = ?", productId);
        String ana = customerToken();
        UUID addressId = createAddress(ana, CUSTOMER_LAT, CUSTOMER_LNG);
        UUID cartId = cartWithOneItem(ana);

        JsonNode quote = quote(ana, cartId, addressId);

        assertMoney(quote.get("total"), "23.90");
        assertMoney(quote.get("goodsVat"), "0.00");
        assertMoney(quote.get("vatIncluded"), "0.81"); // only the fees
    }

    @Test
    void checkoutWithoutQuoteIdKeepsTheCurrentBehaviour() throws Exception {
        String ana = customerToken();
        UUID addressId = createAddress(ana, CUSTOMER_LAT, CUSTOMER_LNG);
        UUID cartId = cartWithOneItem(ana);

        checkout(ana, cartId, addressId, null).andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT quote_id FROM orders WHERE cart_id = ?", UUID.class, cartId)).isNull();
        assertThat(jdbc.queryForObject("SELECT quote_vat_total FROM orders WHERE cart_id = ?", BigDecimal.class, cartId)).isEqualByComparingTo("3.29");
    }

    @Test
    void expiredQuoteAnswersQuoteChangedWithAFreshQuote() throws Exception {
        String ana = customerToken();
        UUID addressId = createAddress(ana, CUSTOMER_LAT, CUSTOMER_LNG);
        UUID cartId = cartWithOneItem(ana);
        UUID quoteId = UUID.fromString(quote(ana, cartId, addressId).get("quoteId").asText());
        jdbc.update("UPDATE checkout_quotes SET created_at = now() - interval '20 minutes', expires_at = now() - interval '10 minutes' WHERE id = ?", quoteId);

        JsonNode problem = body(checkout(ana, cartId, addressId, quoteId).andExpect(status().isConflict()));

        assertThat(problem.get("code").asText()).isEqualTo("quote_changed");
        assertThat(problem.at("/details/reason").asText()).isEqualTo("expired");
        UUID fresh = UUID.fromString(problem.at("/details/quote/quoteId").asText());
        assertThat(fresh).isNotEqualTo(quoteId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE cart_id = ?", Integer.class, cartId)).isZero();

        checkout(ana, cartId, addressId, fresh).andExpect(status().isCreated());
    }

    @Test
    void feesChangedAfterTheQuoteAnswerQuoteChanged() throws Exception {
        String ana = customerToken();
        UUID addressId = createAddress(ana, CUSTOMER_LAT, CUSTOMER_LNG);
        UUID cartId = cartWithOneItem(ana);
        UUID quoteId = UUID.fromString(quote(ana, cartId, addressId).get("quoteId").asText());
        // The vendor publishes a distance band after the quote: delivery 5.00 → 30.00.
        jdbc.update("INSERT INTO delivery_fee_rules (id, tenant_id, vendor_id, min_km, max_km, fee_amount) VALUES (?, ?, ?, 0, 50, 30.00)", UUID.randomUUID(), tenant, vendorId);

        JsonNode problem = body(checkout(ana, cartId, addressId, quoteId).andExpect(status().isConflict()));

        assertThat(problem.get("code").asText()).isEqualTo("quote_changed");
        assertThat(problem.at("/details/reason").asText()).isEqualTo("amounts_changed");
        assertMoney(problem.at("/details/quote/deliveryFee"), "30.00");
        assertThat(jdbc.queryForObject("SELECT quantity_reserved FROM inventory_items WHERE sku_id = ?", Integer.class, skuId)).isZero();
    }

    @Test
    void priceChangedAfterTheQuoteAnswersQuoteChangedWithTheRepricedItems() throws Exception {
        String ana = customerToken();
        UUID addressId = createAddress(ana, CUSTOMER_LAT, CUSTOMER_LNG);
        UUID cartId = cartWithOneItem(ana);
        UUID quoteId = UUID.fromString(quote(ana, cartId, addressId).get("quoteId").asText());
        jdbc.update("UPDATE skus SET price = 20.00 WHERE id = ?", skuId);

        JsonNode problem = body(checkout(ana, cartId, addressId, quoteId).andExpect(status().isConflict()));

        assertThat(problem.get("code").asText()).isEqualTo("quote_changed");
        assertThat(problem.at("/details/reason").asText()).isEqualTo("prices_changed");
        assertMoney(problem.at("/details/items/0/currentUnitPrice"), "20.00");
        assertMoney(problem.at("/details/quote/subtotal"), "20.00");
    }

    @Test
    void quoteOfAnotherCustomerIsNotFoundAndAQuoteOfAnotherCartIsAMismatch() throws Exception {
        String ana = customerToken();
        UUID addressId = createAddress(ana, CUSTOMER_LAT, CUSTOMER_LNG);
        UUID cartId = cartWithOneItem(ana);
        UUID quoteId = UUID.fromString(quote(ana, cartId, addressId).get("quoteId").asText());

        String bruno = customerToken();
        UUID brunoAddress = createAddress(bruno, CUSTOMER_LAT, CUSTOMER_LNG);
        UUID brunoCart = cartWithOneItem(bruno);
        checkout(bruno, brunoCart, brunoAddress, quoteId).andExpect(status().isNotFound());

        UUID otherAddress = createAddress(ana, CUSTOMER_LAT, CUSTOMER_LNG);
        checkout(ana, cartId, otherAddress, quoteId)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("quote_mismatch"));
    }

    // ---- service area + maximum distance (LAC-20) ------------------------------------------------------

    @Test
    void addressOutsideTheVendorZonePolygonIsRefused() throws Exception {
        assignZone(ELSEWHERE_WKT, "ACTIVE");
        String ana = customerToken();
        UUID addressId = createAddress(ana, CUSTOMER_LAT, CUSTOMER_LNG);
        UUID cartId = cartWithOneItem(ana);

        mockMvc.perform(withBearer(get("/api/v1/checkout/quote"), ana).param("cartId", cartId.toString()).param("addressId", addressId.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("out_of_service_area"));
        checkout(ana, cartId, addressId, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("out_of_service_area"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE cart_id = ?", Integer.class, cartId)).isZero();
    }

    @Test
    void addressInsideTheZoneOrAZoneWithoutPolygonIsAccepted() throws Exception {
        assignZone(COVERING_WKT, "ACTIVE");
        String ana = customerToken();
        UUID addressId = createAddress(ana, CUSTOMER_LAT, CUSTOMER_LNG);
        checkout(ana, cartWithOneItem(ana), addressId, null).andExpect(status().isCreated());

        assignZone(null, "ACTIVE"); // no polygon yet: allowed (documented policy)
        String bruno = customerToken();
        checkout(bruno, cartWithOneItem(bruno), createAddress(bruno, CUSTOMER_LAT, CUSTOMER_LNG), null).andExpect(status().isCreated());
    }

    @Test
    void inactiveZoneServesNobody() throws Exception {
        assignZone(COVERING_WKT, "INACTIVE");
        String ana = customerToken();
        checkout(ana, cartWithOneItem(ana), createAddress(ana, CUSTOMER_LAT, CUSTOMER_LNG), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("out_of_service_area"));
    }

    @Test
    void addressBeyondTheVendorMaximumDistanceIsRefused() throws Exception {
        jdbc.update("UPDATE vendors SET max_delivery_distance_km = 1.50 WHERE id = ?", vendorId); // distance ≈ 3.66 km
        String ana = customerToken();
        UUID addressId = createAddress(ana, CUSTOMER_LAT, CUSTOMER_LNG);
        UUID cartId = cartWithOneItem(ana);

        JsonNode problem = body(checkout(ana, cartId, addressId, null).andExpect(status().isUnprocessableEntity()));

        assertThat(problem.get("code").asText()).isEqualTo("address_too_far");
        assertMoney(problem.at("/details/maxDistanceKm"), "1.50");
        assertThat(problem.at("/details/distanceKm").asDouble()).isBetween(3.6, 3.7);

        jdbc.update("UPDATE vendors SET max_delivery_distance_km = 5.00 WHERE id = ?", vendorId);
        checkout(ana, cartId, addressId, null).andExpect(status().isCreated());
    }

    @Test
    void tenantPolicyMaximumDistanceAppliesWhenTheVendorHasNone() throws Exception {
        try {
            jdbc.update("UPDATE fee_policies SET max_delivery_distance_km = 2.00 WHERE tenant_id = ?", tenant);
            String ana = customerToken();
            UUID addressId = createAddress(ana, CUSTOMER_LAT, CUSTOMER_LNG);
            mockMvc.perform(withBearer(get("/api/v1/checkout/quote"), ana).param("cartId", cartWithOneItem(ana).toString()).param("addressId", addressId.toString()))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("address_too_far"));
            // An address without a pin has no distance: allowed (documented).
            String bruno = customerToken();
            checkout(bruno, cartWithOneItem(bruno), createAddress(bruno, null, null), null).andExpect(status().isCreated());
        } finally {
            jdbc.update("UPDATE fee_policies SET max_delivery_distance_km = NULL WHERE tenant_id = ?", tenant);
        }
    }

    // ---- helpers -------------------------------------------------------------------------------------

    private void assignZone(String wkt, String status) {
        UUID zoneId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO zones (id, tenant_id, name, status, geometry_wkt, created_at, updated_at) VALUES (?, ?, 'Zona W6A', ?, ?, ?, ?)",
                zoneId, tenant, status, wkt, now, now);
        jdbc.update("UPDATE vendors SET delivery_zone_id = ? WHERE id = ?", zoneId, vendorId);
    }

    private JsonNode quote(String token, UUID cartId, UUID addressId) throws Exception {
        return body(mockMvc.perform(withBearer(get("/api/v1/checkout/quote"), token)
                        .param("cartId", cartId.toString()).param("addressId", addressId.toString()))
                .andExpect(status().isOk()));
    }

    private ResultActions checkout(String token, UUID cartId, UUID addressId, UUID quoteId) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("cartId", cartId);
        request.put("idempotencyKey", "w6a-" + UUID.randomUUID());
        request.put("addressId", addressId);
        if (quoteId != null) {
            request.put("quoteId", quoteId);
        }
        return mockMvc.perform(withBearer(post("/api/v1/checkout"), token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private UUID cartWithOneItem(String token) throws Exception {
        String cart = mockMvc.perform(withBearer(post("/api/v1/customers/{customerId}/cart/items", UUID.randomUUID()), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vendorId\":\"" + vendorId + "\",\"skuId\":\"" + skuId + "\",\"quantity\":1}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(cart).get("id").asText());
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

    private static void assertMoney(JsonNode node, String expected) {
        assertThat(node).as("money field").isNotNull();
        assertThat(node.isMissingNode()).as("money field present").isFalse();
        assertThat(node.decimalValue()).isEqualByComparingTo(expected);
    }

    private static MockHttpServletRequestBuilder withBearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }
}
