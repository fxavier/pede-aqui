package com.delivery.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * LAC-21 on the production wiring with real Postgres: GET /api/v1/me/orders returns only the caller's own orders
 * (never another customer's or another tenant's), newest first, with a keyset cursor that is stable under inserts and
 * ties on created_at; GET /api/v1/me/orders/{id} returns items, address snapshot, totals and the PIN only while the
 * order is active, and 404 for anyone else's order. Every test seeds fresh tenants (shared container).
 */
class CustomerOrderHistoryE2ETest extends PostgresApiE2ETestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenancyProperties tenancyProperties;

    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
    private final Instant base = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(1, ChronoUnit.DAYS);
    private UUID tenantA;
    private UUID tenantB;
    private UUID vendorA;
    private UUID skuA;
    private String aliceSub;
    private UUID alice;
    private UUID bob;
    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void seed() {
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            Jwt jwt = tokens.get(invocation.<String>getArgument(0));
            if (jwt == null) throw new BadJwtException("unknown test token");
            return jwt;
        });
        // Customers have no tenant_id claim: their tenant is the operator tenant mapped from the OIDC client (ADR-0001).
        tenantA = tenancyProperties.getOperatorTenantId();
        tenantB = tenant();
        vendorA = vendor(tenantA, "Mercearia Alice");
        skuA = sku(tenantA, vendorA);
        aliceSub = "alice-" + UUID.randomUUID();
        alice = profile(tenantA, aliceSub);
        String bobSub = "bob-" + UUID.randomUUID();
        bob = profile(tenantA, bobSub);
        aliceToken = customerToken(aliceSub);
        bobToken = customerToken(bobSub);
    }

    @Test
    void listReturnsOnlyTheCallersOwnOrdersNewestFirstAcrossStablePages() throws Exception {
        List<UUID> mine = new ArrayList<>();
        for (int i = 0; i < 3; i++) mine.add(order(tenantA, vendorA, alice, base.plusSeconds(i), "DELIVERED", null));
        // Three rows share one created_at: only the id tie-break keeps a total order across page boundaries.
        for (int i = 0; i < 3; i++) mine.add(order(tenantA, vendorA, alice, base.plusSeconds(10), "PAYMENT_CONFIRMED", null));
        order(tenantA, vendorA, bob, base.plusSeconds(20), "PAYMENT_CONFIRMED", null);
        // The same Keycloak subject in another tenant is another profile: never mixed in.
        UUID vendorB = vendor(tenantB, "Loja B");
        order(tenantB, vendorB, profile(tenantB, aliceSub), base.plusSeconds(30), "PAYMENT_CONFIRMED", null);
        List<String> expected = jdbc.queryForList(
                "SELECT id::text FROM orders WHERE tenant_id = ? AND customer_id = ? ORDER BY created_at DESC, id DESC", String.class, tenantA, alice);

        List<String> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            JsonNode page = getJson("/api/v1/me/orders?limit=2" + (cursor == null ? "" : "&cursor=" + cursor), aliceToken);
            page.get("items").forEach(item -> seen.add(item.get("id").asText()));
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asText();
            if (pages++ == 0) {
                // A newer order placed after the first page must not shift or duplicate the following pages.
                order(tenantA, vendorA, alice, Instant.now(), "PAYMENT_CONFIRMED", null);
            }
        } while (cursor != null);

        assertThat(pages).isEqualTo(3);
        assertThat(seen).containsExactlyElementsOf(expected);
        assertThat(seen).containsExactlyInAnyOrderElementsOf(mine.stream().map(UUID::toString).toList());
    }

    @Test
    void listRowCarriesStatusCancellationReasonTotalsVendorNameAndItemCount() throws Exception {
        UUID rejected = order(tenantA, vendorA, alice, base, "CANCELLED", "SELLER_REJECTED");
        item(tenantA, rejected);
        item(tenantA, rejected);

        mockMvc.perform(bearer(get("/api/v1/me/orders"), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(rejected.toString()))
                .andExpect(jsonPath("$.items[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.items[0].cancellationReason").value("SELLER_REJECTED"))
                .andExpect(jsonPath("$.items[0].vendorName").value("Mercearia Alice"))
                .andExpect(jsonPath("$.items[0].itemCount").value(2))
                .andExpect(jsonPath("$.items[0].total").value(100.0))
                .andExpect(jsonPath("$.items[0].subtotal").value(90.0))
                .andExpect(jsonPath("$.items[0].createdAt").exists())
                .andExpect(jsonPath("$.items[0].deliveryCode").doesNotExist())
                .andExpect(jsonPath("$.items[0].deliveryAddress").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").isEmpty());

        // A customer with no orders gets an empty page, not an error.
        mockMvc.perform(bearer(get("/api/v1/me/orders"), bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void detailReturnsItemsAddressTotalsAndActivePinOnlyToTheOwner() throws Exception {
        UUID active = order(tenantA, vendorA, alice, base, "PREPARING", null);
        item(tenantA, active);

        mockMvc.perform(bearer(get("/api/v1/me/orders/" + active), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(active.toString()))
                .andExpect(jsonPath("$.vendorName").value("Mercearia Alice"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productName").value("Arroz"))
                .andExpect(jsonPath("$.deliveryAddress.line1").value("Av. Julius Nyerere 100"))
                .andExpect(jsonPath("$.deliveryAddress.city").value("Maputo"))
                .andExpect(jsonPath("$.deliveryCode").value("123456"))
                .andExpect(jsonPath("$.deliveryFee").value(7.5))
                .andExpect(jsonPath("$.serviceFee").value(2.5))
                .andExpect(jsonPath("$.deliveryFeeBasis").value("DISTANCE_RULE"))
                .andExpect(jsonPath("$.distanceKm").value(3.2))
                .andExpect(jsonPath("$.total").value(100.0));

        // Same tenant, other customer: 404 (existence not revealed), not 403.
        mockMvc.perform(bearer(get("/api/v1/me/orders/" + active), bobToken))
                .andExpect(status().isNotFound());
        // An order of the same subject's profile in another tenant is out of scope: 404.
        UUID foreign = order(tenantB, vendor(tenantB, "Loja B"), profile(tenantB, aliceSub), base, "PREPARING", null);
        mockMvc.perform(bearer(get("/api/v1/me/orders/" + foreign), aliceToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(bearer(get("/api/v1/me/orders/" + UUID.randomUUID()), aliceToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void detailOfAFinishedOrderHasNoPin() throws Exception {
        // markDelivered/markCancelled clear the display column; the API must reflect it, not re-derive a PIN.
        UUID delivered = order(tenantA, vendorA, alice, base, "DELIVERED", null);
        jdbc.update("UPDATE orders SET delivery_confirmation_code_display = NULL WHERE id = ?", delivered);

        mockMvc.perform(bearer(get("/api/v1/me/orders/" + delivered), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveryCode").isEmpty());
    }

    @Test
    void nonCustomersAreForbidden() throws Exception {
        String admin = token("admin-" + UUID.randomUUID(), Map.of("tenant_id", tenantA.toString()), "ADMIN");
        mockMvc.perform(bearer(get("/api/v1/me/orders"), admin)).andExpect(status().isForbidden());
        mockMvc.perform(bearer(get("/api/v1/me/orders/" + UUID.randomUUID()), admin)).andExpect(status().isForbidden());
    }

    // --- helpers ---

    private JsonNode getJson(String path, String token) throws Exception {
        String body = mockMvc.perform(bearer(get(path), token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private UUID tenant() {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, 'Hist', ?, 'ACTIVE', 'MZN', ?, ?)",
                id, "hist-" + id, now, now);
        return id;
    }

    private UUID vendor(UUID tenantId, String name) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO vendors (id, tenant_id, name, status, verification_status, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', 'APPROVED', ?, ?)",
                id, tenantId, name, now, now);
        return id;
    }

    private UUID sku(UUID tenantId, UUID vendorId) {
        UUID categoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO categories (id, tenant_id, name, slug, active, created_at, updated_at) VALUES (?, ?, 'C', ?, TRUE, ?, ?)",
                categoryId, tenantId, "slug-" + categoryId, now, now);
        jdbc.update("""
                INSERT INTO products (id, tenant_id, vendor_id, category_id, name, status,
                                      requires_prescription_metadata, manual_validation_required, prohibited_fuel, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Arroz', 'ACTIVE', FALSE, FALSE, FALSE, ?, ?)""",
                productId, tenantId, vendorId, categoryId, now, now);
        jdbc.update("INSERT INTO skus (id, tenant_id, product_id, sku_code, name, price, active, created_at, updated_at) VALUES (?, ?, ?, ?, 'Arroz 1kg', 18.00, TRUE, ?, ?)",
                skuId, tenantId, productId, "sku-" + skuId, now, now);
        return skuId;
    }

    private UUID profile(UUID tenantId, String keycloakUserId) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'Hist User', 'ACTIVE', ?, ?)",
                id, tenantId, keycloakUserId, id + "@example.test", now, now);
        return id;
    }

    private UUID order(UUID tenantId, UUID vendorId, UUID customerId, Instant at, String status, String cancellationReason) {
        UUID id = UUID.randomUUID();
        Timestamp ts = Timestamp.from(at);
        jdbc.update("""
                INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, status, cancellation_reason, subtotal, fees, taxes,
                                    discounts, discount_total, total, checkout_idempotency_key, delivery_confirmation_code_hash,
                                    delivery_confirmation_code_display, delivery_recipient_name, delivery_phone, delivery_line1,
                                    delivery_city, quote_engine_version, quote_delivery_fee, quote_service_fee, quote_delivery_fee_basis,
                                    quote_distance_km, quoted_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 90, 10, 0, 0, 0, 100, ?, 'hash', '123456', 'Alice', '+258840000000',
                        'Av. Julius Nyerere 100', 'Maputo', 'test', 7.50, 2.50, 'DISTANCE_RULE', 3.20, ?, ?, ?)""",
                id, tenantId, "HIST-" + id.toString().substring(0, 8), customerId, vendorId, status, cancellationReason,
                "hist-" + id, ts, ts, ts);
        return id;
    }

    private void item(UUID tenantId, UUID orderId) {
        jdbc.update("INSERT INTO order_items (id, order_id, tenant_id, sku_id, product_name_snapshot, sku_name_snapshot, unit_price_snapshot, quantity, line_total, created_at) VALUES (?, ?, ?, ?, 'Arroz', 'Arroz 1kg', 18.00, 5, 90.00, ?)",
                UUID.randomUUID(), orderId, tenantId, skuA, Timestamp.from(Instant.now()));
    }

    private String customerToken(String subject) {
        return token(subject, Map.of("azp", "pede-aqui-web"), "CUSTOMER");
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
