package com.delivery.rating;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.PostgresApiE2ETestBase;
import com.delivery.common.security.TenancyProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * LAC-28 end to end on real Postgres: a customer rates a DELIVERED order once inside the window, edits it within the
 * edit window, the vendor/courier aggregates stay exact (also under concurrent ratings) and are what search and the
 * public vendor profile show, OPS/SUPPORT moderate comments (audited) and the vendor replies once.
 */
@RecordApplicationEvents
class RatingApiE2ETest extends PostgresApiE2ETestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenancyProperties tenancyProperties;
    @Autowired private ApplicationEvents applicationEvents;

    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
    private UUID tenant;
    private UUID vendorId;
    private UUID categoryId;
    private UUID courierId;
    private String aliceSub;
    private UUID alice;
    private String aliceToken;

    @BeforeEach
    void seed() {
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            Jwt jwt = tokens.get(invocation.<String>getArgument(0));
            if (jwt == null) throw new BadJwtException("unknown test token");
            return jwt;
        });
        tenant = tenancyProperties.getOperatorTenantId();
        vendorId = vendor("Loja Avaliada");
        categoryId = activeProduct(vendorId);
        courierId = courier();
        aliceSub = "alice-" + UUID.randomUUID();
        alice = profile(aliceSub);
        aliceToken = customerToken(aliceSub);
    }

    @Test
    void customerRatesADeliveredOrderOnceAndAggregatesAreExact() throws Exception {
        UUID order = deliveredOrder(alice, vendorId, courierId, Instant.now().minus(1, ChronoUnit.DAYS));

        mockMvc.perform(bearer(get("/api/v1/me/orders/{id}/rating", order), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateable").value(true))
                .andExpect(jsonPath("$.courierRateable").value(true))
                .andExpect(jsonPath("$.rateableUntil").isNotEmpty())
                .andExpect(jsonPath("$.rating").isEmpty());

        mockMvc.perform(rate(post("/api/v1/me/orders/{id}/rating", order), aliceToken, 4, 5, "  Muito bom  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vendorStars").value(4))
                .andExpect(jsonPath("$.courierStars").value(5))
                .andExpect(jsonPath("$.comment").value("Muito bom"))
                .andExpect(jsonPath("$.vendorId").value(vendorId.toString()))
                .andExpect(jsonPath("$.courierId").value(courierId.toString()));

        mockMvc.perform(rate(post("/api/v1/me/orders/{id}/rating", order), aliceToken, 1, null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("rating_already_submitted"));

        mockMvc.perform(bearer(get("/api/v1/me/orders/{id}/rating", order), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateable").value(false))
                .andExpect(jsonPath("$.notRateableReason").value("rating_already_submitted"))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.rating.vendorStars").value(4));

        UUID second = deliveredOrder(alice, vendorId, null, Instant.now().minus(2, ChronoUnit.HOURS));
        mockMvc.perform(rate(post("/api/v1/me/orders/{id}/rating", second), aliceToken, 5, null, null))
                .andExpect(status().isCreated());

        assertVendorAggregate(vendorId, 4.5, 2);
        assertCourierAggregate(courierId, 5.0, 1);
        // The rater is the profile id (V072 FK), never the Keycloak subject.
        assertThat(jdbc.queryForObject("SELECT rater_user_id FROM ratings WHERE order_id = ?", UUID.class, order)).isEqualTo(alice);
        assertThat(applicationEvents.stream(RatingSubmitted.class)
                .filter(e -> e.orderId().equals(order))
                .map(RatingSubmitted::edited)).containsExactly(false);
    }

    @Test
    void searchAndThePublicVendorProfileShowTheRealAggregate() throws Exception {
        for (int stars : new int[] {5, 4, 4}) {
            String sub = "c-" + UUID.randomUUID();
            UUID customer = profile(sub);
            UUID order = deliveredOrder(customer, vendorId, null, Instant.now().minus(1, ChronoUnit.HOURS));
            mockMvc.perform(rate(post("/api/v1/me/orders/{id}/rating", order), customerToken(sub), stars, null, null))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/v1/catalog/vendors/{id}", vendorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(4.33))
                .andExpect(jsonPath("$.ratingCount").value(3));
        mockMvc.perform(get("/api/v1/search/vendors").param("category", categoryId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendors.length()").value(1))
                .andExpect(jsonPath("$.vendors[0].vendorId").value(vendorId.toString()))
                .andExpect(jsonPath("$.vendors[0].rating").value(4.33))
                .andExpect(jsonPath("$.vendors[0].ratingCount").value(3));
        mockMvc.perform(get("/api/v1/search/vendors").param("category", categoryId.toString()).param("minRating", "4.5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendors.length()").value(0));
    }

    @Test
    void ratingIsRefusedBeforeDeliveryAfterTheWindowAndForOtherCustomersOrders() throws Exception {
        UUID preparing = order(alice, vendorId, "PREPARING");
        mockMvc.perform(rate(post("/api/v1/me/orders/{id}/rating", preparing), aliceToken, 5, null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("order_not_delivered"));
        mockMvc.perform(bearer(get("/api/v1/me/orders/{id}/rating", preparing), aliceToken))
                .andExpect(jsonPath("$.rateable").value(false))
                .andExpect(jsonPath("$.notRateableReason").value("order_not_delivered"));

        UUID old = deliveredOrder(alice, vendorId, null, Instant.now().minus(8, ChronoUnit.DAYS));
        mockMvc.perform(rate(post("/api/v1/me/orders/{id}/rating", old), aliceToken, 5, null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("rating_window_closed"));

        UUID recent = deliveredOrder(alice, vendorId, null, Instant.now().minus(1, ChronoUnit.HOURS));
        String bobSub = "bob-" + UUID.randomUUID();
        profile(bobSub);
        mockMvc.perform(rate(post("/api/v1/me/orders/{id}/rating", recent), customerToken(bobSub), 1, null, null))
                .andExpect(status().isNotFound());
        mockMvc.perform(bearer(get("/api/v1/me/orders/{id}/rating", recent), customerToken(bobSub)))
                .andExpect(status().isNotFound());

        // No courier delivered this order: courier stars are a 400, out-of-range stars and long comments too.
        mockMvc.perform(rate(post("/api/v1/me/orders/{id}/rating", recent), aliceToken, 5, 4, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("courier_not_rateable"));
        mockMvc.perform(rate(post("/api/v1/me/orders/{id}/rating", recent), aliceToken, 6, null, null))
                .andExpect(status().isBadRequest());
        mockMvc.perform(rate(post("/api/v1/me/orders/{id}/rating", recent), aliceToken, 5, null, "x".repeat(501)))
                .andExpect(status().isBadRequest());

        assertVendorAggregate(vendorId, 0.0, 0);
        mockMvc.perform(rate(post("/api/v1/me/orders/{id}/rating", recent), staffToken("OPS"), 5, null, null))
                .andExpect(status().isForbidden());
    }

    @Test
    void editIsAllowedWithinTheEditWindowAndRecomputesTheAggregates() throws Exception {
        UUID order = deliveredOrder(alice, vendorId, courierId, Instant.now().minus(1, ChronoUnit.DAYS));
        mockMvc.perform(rate(post("/api/v1/me/orders/{id}/rating", order), aliceToken, 2, 2, "Fraco")).andExpect(status().isCreated());

        mockMvc.perform(rate(put("/api/v1/me/orders/{id}/rating", order), aliceToken, 5, 4, "Afinal correu bem"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendorStars").value(5))
                .andExpect(jsonPath("$.courierStars").value(4))
                .andExpect(jsonPath("$.comment").value("Afinal correu bem"));
        assertVendorAggregate(vendorId, 5.0, 1);
        assertCourierAggregate(courierId, 4.0, 1);
        assertThat(applicationEvents.stream(RatingSubmitted.class).filter(e -> e.orderId().equals(order))
                .map(RatingSubmitted::edited)).containsExactly(false, true);

        jdbc.update("UPDATE ratings SET created_at = ? WHERE order_id = ?", Timestamp.from(Instant.now().minus(25, ChronoUnit.HOURS)), order);
        mockMvc.perform(rate(put("/api/v1/me/orders/{id}/rating", order), aliceToken, 1, null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("rating_edit_window_closed"));
        mockMvc.perform(bearer(get("/api/v1/me/orders/{id}/rating", order), aliceToken))
                .andExpect(jsonPath("$.editable").value(false));

        UUID unrated = deliveredOrder(alice, vendorId, null, Instant.now());
        mockMvc.perform(rate(put("/api/v1/me/orders/{id}/rating", unrated), aliceToken, 1, null, null))
                .andExpect(status().isNotFound());
    }

    @Test
    void concurrentRatingsOfOneVendorKeepTheAggregateExact() throws Exception {
        int raters = 8;
        List<String> tokensForRaters = new ArrayList<>();
        List<UUID> orders = new ArrayList<>();
        for (int i = 0; i < raters; i++) {
            String sub = "conc-" + UUID.randomUUID();
            UUID customer = profile(sub);
            tokensForRaters.add(customerToken(sub));
            orders.add(deliveredOrder(customer, vendorId, courierId, Instant.now().minus(1, ChronoUnit.HOURS)));
        }
        // The same customer racing on one order: exactly one wins.
        String racerToken = tokensForRaters.get(0);
        UUID racedOrder = orders.get(0);

        ExecutorService pool = Executors.newFixedThreadPool(raters + 2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < raters; i++) {
                int stars = (i % 5) + 1;
                String token = tokensForRaters.get(i);
                UUID order = orders.get(i);
                results.add(pool.submit(call(start, () -> mockMvc.perform(rate(post("/api/v1/me/orders/{id}/rating", order), token, stars, stars, null))
                        .andReturn().getResponse().getStatus())));
            }
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(call(start, () -> mockMvc.perform(rate(post("/api/v1/me/orders/{id}/rating", racedOrder), racerToken, 3, null, null))
                        .andReturn().getResponse().getStatus())));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) statuses.add(result.get(60, TimeUnit.SECONDS));
            assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(raters);
            assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }

        Map<String, Object> expected = jdbc.queryForMap(
                "SELECT ROUND(AVG(stars)::numeric, 2) AS avg, COUNT(*) AS cnt FROM ratings WHERE rated_vendor_id = ?", vendorId);
        assertThat(((Number) expected.get("cnt")).intValue()).isEqualTo(raters);
        assertVendorAggregate(vendorId, ((Number) expected.get("avg")).doubleValue(), raters);
        Map<String, Object> expectedCourier = jdbc.queryForMap(
                "SELECT ROUND(AVG(courier_stars)::numeric, 2) AS avg, COUNT(*) AS cnt FROM ratings WHERE rated_courier_id = ? AND courier_stars IS NOT NULL", courierId);
        assertCourierAggregate(courierId, ((Number) expectedCourier.get("avg")).doubleValue(), ((Number) expectedCourier.get("cnt")).intValue());
    }

    @Test
    void moderationHidesAndUnhidesCommentsWithAuditAndVendorRepliesOnce() throws Exception {
        UUID order = deliveredOrder(alice, vendorId, null, Instant.now().minus(1, ChronoUnit.HOURS));
        String ratingId = objectMapper.readTree(mockMvc.perform(rate(post("/api/v1/me/orders/{id}/rating", order), aliceToken, 1, null, "Comentario ofensivo"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        String merchant = merchantToken(vendorId);
        String ops = staffToken("OPS");

        mockMvc.perform(bearer(post("/api/v1/ops/ratings/{id}/hide", ratingId), ops).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"linguagem ofensiva\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commentHidden").value(true))
                .andExpect(jsonPath("$.commentHiddenReason").value("linguagem ofensiva"))
                .andExpect(jsonPath("$.comment").value("Comentario ofensivo"));
        // Repeating is a no-op: still one audit entry.
        mockMvc.perform(bearer(post("/api/v1/ops/ratings/{id}/hide", ratingId), staffToken("SUPPORT")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"outra\"}"))
                .andExpect(status().isOk());
        assertThat(audits(ratingId, "RATING_COMMENT_HIDDEN")).isEqualTo(1);

        JsonNode vendorPage = objectMapper.readTree(mockMvc.perform(bearer(get("/api/v1/vendor/ratings"), merchant))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode seenByVendor = vendorPage.get("items").get(0);
        assertThat(seenByVendor.get("id").asText()).isEqualTo(ratingId);
        assertThat(seenByVendor.get("comment").isNull()).isTrue();
        assertThat(seenByVendor.get("commentHidden").asBoolean()).isTrue();
        assertThat(seenByVendor.get("commentHiddenReason").isNull()).isTrue();
        // The author still sees their own comment; the stars keep counting.
        mockMvc.perform(bearer(get("/api/v1/me/orders/{id}/rating", order), aliceToken))
                .andExpect(jsonPath("$.rating.comment").value("Comentario ofensivo"))
                .andExpect(jsonPath("$.rating.commentHidden").value(true));
        assertVendorAggregate(vendorId, 1.0, 1);
        mockMvc.perform(bearer(get("/api/v1/ops/ratings").param("vendorId", vendorId.toString()).param("hidden", "true"), ops))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(ratingId));

        mockMvc.perform(bearer(post("/api/v1/ops/ratings/{id}/unhide", ratingId), ops))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commentHidden").value(false));
        assertThat(audits(ratingId, "RATING_COMMENT_UNHIDDEN")).isEqualTo(1);
        mockMvc.perform(bearer(get("/api/v1/ops/ratings").param("vendorId", vendorId.toString()).param("hidden", "true"), ops))
                .andExpect(jsonPath("$.items.length()").value(0));

        mockMvc.perform(bearer(post("/api/v1/vendor/ratings/{id}/reply", ratingId), merchant).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reply\":\"Lamentamos, vamos melhorar\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendorReply").value("Lamentamos, vamos melhorar"))
                .andExpect(jsonPath("$.vendorRepliedAt").isNotEmpty());
        assertThat(audits(ratingId, "RATING_VENDOR_REPLIED")).isEqualTo(1);
        mockMvc.perform(bearer(post("/api/v1/vendor/ratings/{id}/reply", ratingId), merchant).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reply\":\"Segunda\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("rating_reply_exists"));
        // Another vendor's admin cannot see or reply: 404.
        UUID otherVendor = vendor("Outra Loja");
        mockMvc.perform(bearer(post("/api/v1/vendor/ratings/{id}/reply", ratingId), merchantToken(otherVendor)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reply\":\"Intruso\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(bearer(get("/api/v1/ops/ratings"), aliceToken)).andExpect(status().isForbidden());
        mockMvc.perform(bearer(post("/api/v1/ops/ratings/{id}/hide", ratingId), staffToken("FINANCE")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"x\"}")).andExpect(status().isForbidden());
    }

    @Test
    void ratingWithoutCommentCannotBeHidden() throws Exception {
        UUID order = deliveredOrder(alice, vendorId, null, Instant.now().minus(1, ChronoUnit.HOURS));
        String ratingId = objectMapper.readTree(mockMvc.perform(rate(post("/api/v1/me/orders/{id}/rating", order), aliceToken, 3, null, "   "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comment").isEmpty())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(bearer(post("/api/v1/ops/ratings/{id}/hide", ratingId), staffToken("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("rating_comment_absent"));
        mockMvc.perform(bearer(post("/api/v1/ops/ratings/{id}/hide", UUID.randomUUID()), staffToken("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    // --- assertions ---

    private void assertVendorAggregate(UUID vendor, double average, int count) {
        Map<String, Object> row = jdbc.queryForMap("SELECT rating, rating_count FROM vendors WHERE id = ?", vendor);
        assertThat(((Number) row.get("rating")).doubleValue()).isEqualTo(average);
        assertThat(((Number) row.get("rating_count")).intValue()).isEqualTo(count);
    }

    private void assertCourierAggregate(UUID courier, double average, int count) {
        Map<String, Object> row = jdbc.queryForMap("SELECT rating, rating_count FROM couriers WHERE id = ?", courier);
        assertThat(((Number) row.get("rating")).doubleValue()).isEqualTo(average);
        assertThat(((Number) row.get("rating_count")).intValue()).isEqualTo(count);
    }

    private int audits(String targetId, String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE target_id = ? AND action = ?", Integer.class, targetId, action);
    }

    // --- seeding ---

    private UUID vendor(String name) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO vendors (id, tenant_id, name, status, verification_status, available, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', 'APPROVED', TRUE, ?, ?)",
                id, tenant, name, now, now);
        return id;
    }

    /** Gives the vendor one ACTIVE product in a category of its own, so a category search returns only this vendor. */
    private UUID activeProduct(UUID vendor) {
        UUID category = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO categories (id, tenant_id, name, slug, active, created_at, updated_at) VALUES (?, ?, 'Rating', ?, TRUE, ?, ?)",
                category, tenant, "rating-" + category, now, now);
        jdbc.update("""
                INSERT INTO products (id, tenant_id, vendor_id, category_id, name, status,
                                      requires_prescription_metadata, manual_validation_required, prohibited_fuel, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Pao', 'ACTIVE', FALSE, FALSE, FALSE, ?, ?)""",
                UUID.randomUUID(), tenant, vendor, category, now, now);
        return category;
    }

    private UUID courier() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO couriers (id, tenant_id, user_profile_id, verification_status, available, created_at, updated_at) VALUES (?, ?, ?, 'APPROVED', TRUE, now(), now())",
                id, tenant, profile("courier-" + id));
        return id;
    }

    private UUID profile(String keycloakUserId) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'Rating User', 'ACTIVE', ?, ?)",
                id, tenant, keycloakUserId, id + "@example.test", now, now);
        return id;
    }

    private UUID order(UUID customer, UUID vendor, String status) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now().minus(Duration.ofDays(10)));
        jdbc.update("""
                INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, status, subtotal, fees, taxes, discounts,
                                    discount_total, total, checkout_idempotency_key, delivery_confirmation_code_hash,
                                    delivery_confirmation_code_display, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 90, 10, 0, 0, 0, 100, ?, 'hash', NULL, ?, ?)""",
                id, tenant, "RAT-" + id.toString().substring(0, 8), customer, vendor, status, "rat-" + id, now, now);
        return id;
    }

    /** A DELIVERED order whose DELIVERED history row is at {@code deliveredAt}, with a delivery (courier optional). */
    private UUID deliveredOrder(UUID customer, UUID vendor, UUID courier, Instant deliveredAt) {
        UUID id = order(customer, vendor, "DELIVERED");
        jdbc.update("INSERT INTO order_status_history (id, tenant_id, order_id, from_status, to_status, actor_type, created_at) VALUES (?, ?, ?, 'DELIVERING', 'DELIVERED', 'SYSTEM', ?)",
                UUID.randomUUID(), tenant, id, Timestamp.from(deliveredAt));
        jdbc.update("INSERT INTO deliveries (id, tenant_id, order_id, courier_id, status, confirmation_code_hash, confirmation_attempts, created_at, updated_at) VALUES (?, ?, ?, ?, 'DELIVERED', 'hash', 0, now(), now())",
                UUID.randomUUID(), tenant, id, courier);
        return id;
    }

    // --- tokens and requests ---

    private String merchantToken(UUID vendor) {
        String sub = "merchant-" + UUID.randomUUID();
        UUID profileId = profile(sub);
        jdbc.update("INSERT INTO app_user_profile_roles (app_user_profile_id, role) VALUES (?, 'VENDOR_ADMIN')", profileId);
        jdbc.update("INSERT INTO merchant_users (id, tenant_id, vendor_id, app_user_profile_id, role) VALUES (?, ?, ?, ?, 'OWNER')",
                UUID.randomUUID(), tenant, vendor, profileId);
        return token(sub, Map.of("azp", "delivery-app", "tenant_id", tenant.toString()), "VENDOR_ADMIN");
    }

    private String staffToken(String role) {
        return token(role.toLowerCase() + "-" + UUID.randomUUID(), Map.of("azp", "delivery-app", "tenant_id", tenant.toString()), role);
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

    private MockHttpServletRequestBuilder rate(MockHttpServletRequestBuilder request, String token, Integer vendorStars,
                                               Integer courierStars, String comment) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("vendorStars", vendorStars);
        if (courierStars != null) body.put("courierStars", courierStars);
        if (comment != null) body.put("comment", comment);
        return bearer(request, token).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    private static <T> Callable<T> call(CountDownLatch start, Callable<T> body) {
        return () -> {
            start.await();
            return body.call();
        };
    }

    private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }
}
