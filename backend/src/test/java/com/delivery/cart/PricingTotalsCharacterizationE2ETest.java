package com.delivery.cart;

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
 * coupon on cart and checkout, the GET /cart/pricing preview, and the search delivery fee.
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
    void cartTotalsUseTheFlatDeliveryFeeServiceFeeAndTax() throws Exception {
        JsonNode cart = addToCart(customerToken());

        // subtotal 18.00 + fees (5.00 delivery + 0.90 service) + taxes 1.44 = 25.34
        assertMoney(cart.get("subtotal"), "18.00");
        assertMoney(cart.get("fees"), "5.90");
        assertMoney(cart.get("taxes"), "1.44");
        assertMoney(cart.get("total"), "25.34");
    }

    @Test
    void legacyCheckoutWithoutAddressChargesTheCartTotals() throws Exception {
        String ana = customerToken();
        UUID cartId = UUID.fromString(addToCart(ana).get("id").asText());

        mockMvc.perform(withBearer(post("/api/v1/checkout"), ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartId\":\"" + cartId + "\",\"idempotencyKey\":\"char-legacy-" + cartId + "\"}"))
                .andExpect(status().isCreated());

        assertOrderTotals(cartId, "5.90", "1.44", "0", "25.34");
    }

    @Test
    void checkoutWithAnAddressChargesTheCartTotals() throws Exception {
        String ana = customerToken();
        UUID addressId = createAddress(ana, CUSTOMER_LAT, CUSTOMER_LNG);
        UUID cartId = UUID.fromString(addToCart(ana).get("id").asText());

        checkout(ana, cartId, addressId);

        assertOrderTotals(cartId, "5.90", "1.44", "0", "25.34");
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
        assertMoney(cartPricing.get("taxes"), "1.44");
        assertMoney(cartPricing.get("discountTotal"), "1.80");
        assertMoney(cartPricing.get("total"), "23.54");

        checkout(ana, cartId, addressId);

        assertOrderTotals(cartId, "5.90", "1.44", "1.80", "23.54");
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
        assertMoney(preview.get("tax"), "8.00");
        assertMoney(preview.get("discount"), "0");
        assertMoney(preview.get("total"), "118.00");
    }

    @Test
    void pricingPreviewHonoursTheMvp10MagicCoupon() throws Exception {
        String ana = customerToken();
        mockMvc.perform(withBearer(get("/api/v1/customers/{customerId}/cart/pricing", UUID.randomUUID()), ana)
                        .param("subtotal", "100.00").param("couponCode", "mvp10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discount").value(10.0))
                .andExpect(jsonPath("$.total").value(108.0));
    }

    @Test
    void searchShowsADistanceBasedDeliveryFee() throws Exception {
        // 3.66 km (haversine) from the vendor: 2.50 + 0.50 * 3.66 = 4.33 — a different formula from checkout's flat 5.00.
        JsonNode row = searchRow(CUSTOMER_LAT, CUSTOMER_LNG);
        assertThat(row.get("distanceKm").asDouble()).isEqualTo(3.66);
        assertMoney(row.get("deliveryFee"), "4.33");
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

    private static MockHttpServletRequestBuilder withBearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }
}
