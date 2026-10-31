package com.delivery.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
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
 * LAC-29 characterization (written before the coupon consolidation and kept unchanged after it): pins the discount
 * and the totals that {@code CheckoutDiscountResolver} produces on cart and checkout for existing spec-002 promotions,
 * on real Postgres, for one vendor selling an 18.00 SKU (fees 5.90, VAT included, total 23.90 without discount).
 * Every promotion is vendor-scoped so nothing leaks into other tests sharing the operator tenant.
 */
class PromotionDiscountCharacterizationE2ETest extends PostgresApiE2ETestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenancyProperties tenancyProperties;

    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
    private UUID tenant;
    private UUID vendorId;
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
        UUID categoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        jdbc.update("INSERT INTO vendors (id, tenant_id, name, status, verification_status, available, created_at, updated_at) "
                + "VALUES (?, ?, 'Loja Promo', 'ACTIVE', 'APPROVED', TRUE, ?, ?)", vendorId, tenant, now, now);
        jdbc.update("INSERT INTO categories (id, tenant_id, name, slug, active, created_at, updated_at) VALUES (?, ?, 'Mercearia', ?, TRUE, ?, ?)",
                categoryId, tenant, "promo-" + categoryId, now, now);
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
    void percentageCouponIsCappedByMaxDiscountAmount() throws Exception {
        String code = promotion("PERCENTAGE", "50", new BigDecimal("5.00"), null, true);
        assertCouponTotals(code, "5.00", "18.90");
    }

    @Test
    void fixedAmountCouponDiscountsItsValue() throws Exception {
        String code = promotion("FIXED_AMOUNT", "3", null, null, true);
        assertCouponTotals(code, "3.00", "20.90");
    }

    @Test
    void fixedAmountAboveTheSubtotalIsClampedToTheSubtotal() throws Exception {
        String code = promotion("FIXED_AMOUNT", "25", null, null, true);
        assertCouponTotals(code, "18.00", "5.90");
    }

    @Test
    void automaticVendorPromotionAppliesWithoutACoupon() throws Exception {
        promotion("PERCENTAGE", "10", null, null, false);
        String ana = customerToken();
        UUID cartId = cartId(ana);

        checkout(ana, cartId);

        assertOrderTotals(cartId, "1.80", "22.10");
    }

    @Test
    void couponBelowTheMinimumOrderIsRejected() throws Exception {
        String code = promotion("PERCENTAGE", "10", null, new BigDecimal("20.00"), true);
        String ana = customerToken();
        UUID cartId = cartId(ana);

        mockMvc.perform(withBearer(post("/api/v1/cart/{cartId}/coupon", cartId), ana)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("coupon_min_order_not_met"));
    }

    // --- helpers ---

    private void assertCouponTotals(String code, String discount, String total) throws Exception {
        String ana = customerToken();
        UUID cartId = cartId(ana);
        String body = mockMvc.perform(withBearer(post("/api/v1/cart/{cartId}/coupon", cartId), ana)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code.toLowerCase() + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode pricing = objectMapper.readTree(body);
        assertThat(pricing.get("discountTotal").decimalValue()).isEqualByComparingTo(discount);
        assertThat(pricing.get("total").decimalValue()).isEqualByComparingTo(total);

        checkout(ana, cartId);

        assertOrderTotals(cartId, discount, total);
    }

    /** Inserts an ACTIVE vendor-scoped spec-002 promotion valid now; returns its code (null for automatic). */
    private String promotion(String type, String value, BigDecimal maxDiscount, BigDecimal minOrder, boolean coupon) {
        String code = coupon ? "CHR" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() : null;
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO promotion (id, tenant_id, vendor_id, name, code, type, value, scope, min_order_total, max_discount_amount,
                                       starts_at, ends_at, status, created_at, updated_at)
                VALUES (?, ?, ?, 'Characterization', ?, ?, ?, 'ORDER', ?, ?, ?, ?, 'ACTIVE', ?, ?)""",
                UUID.randomUUID(), tenant, vendorId, code, type, new BigDecimal(value), minOrder, maxDiscount,
                Timestamp.from(now.minus(1, ChronoUnit.DAYS)), Timestamp.from(now.plus(1, ChronoUnit.DAYS)),
                Timestamp.from(now), Timestamp.from(now));
        return code;
    }

    private void assertOrderTotals(UUID cartId, String discountTotal, String total) {
        Map<String, Object> order = jdbc.queryForMap("SELECT o.subtotal, o.fees, o.taxes, o.discount_total, o.total, p.amount "
                + "FROM orders o JOIN payments p ON p.order_id = o.id WHERE o.cart_id = ?", cartId);
        assertThat((BigDecimal) order.get("subtotal")).isEqualByComparingTo("18.00");
        assertThat((BigDecimal) order.get("fees")).isEqualByComparingTo("5.90");
        assertThat((BigDecimal) order.get("taxes")).isEqualByComparingTo("0");
        assertThat((BigDecimal) order.get("discount_total")).isEqualByComparingTo(discountTotal);
        assertThat((BigDecimal) order.get("total")).isEqualByComparingTo(total);
        assertThat((BigDecimal) order.get("amount")).isEqualByComparingTo(total);
    }

    private void checkout(String token, UUID cartId) throws Exception {
        mockMvc.perform(withBearer(post("/api/v1/checkout"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartId\":\"" + cartId + "\",\"idempotencyKey\":\"chr-" + cartId + "\"}"))
                .andExpect(status().isCreated());
    }

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
        Map<String, Object> claims = new HashMap<>(Map.of("azp", "pede-aqui-web", "email", "promo-" + subject + "@example.test", "name", "Cliente Promo"));
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
