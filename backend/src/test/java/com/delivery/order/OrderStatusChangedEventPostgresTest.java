package com.delivery.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.PostgresApiE2ETestBase;
import com.delivery.common.TestParents;
import com.delivery.common.jobs.EventPublicationRetryJob;
import com.delivery.common.security.TenancyProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * LAC-47 end to end on real Postgres: a vendor accepting an order publishes {@link OrderStatusChanged} in the
 * business transaction; the JDBC event publication registry persists one row per listener in that transaction and
 * marks it completed after the {@code @ApplicationModuleListener} succeeds. A listener that fails leaves its row
 * incomplete (the other listener is unaffected) and {@link EventPublicationRetryJob} redelivers it.
 *
 * <p>Own context (adds a test listener) with a 3-connection pool so it stays well inside the shared container's
 * max_connections next to the shared E2E context.
 */
@Import(OrderStatusChangedEventPostgresTest.FlakyListenerConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=3",
        "app.jobs.event-publication-retry.older-than=PT0S"})
class OrderStatusChangedEventPostgresTest extends PostgresApiE2ETestBase {

    private static final String METRICS_LISTENER = "OrderStatusMetricsListener";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenancyProperties tenancyProperties;
    @Autowired private MeterRegistry meterRegistry;
    @Autowired private EventPublicationRetryJob retryJob;

    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
    private UUID tenant;
    private UUID orderId;
    private String merchant;

    @BeforeEach
    void seed() {
        FlakyListener.failuresRemaining.set(0);
        FlakyListener.attempts.clear();
        tenant = tenancyProperties.getOperatorTenantId();
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            Jwt jwt = tokens.get(invocation.<String>getArgument(0));
            if (jwt == null) throw new BadJwtException("unknown test token");
            return jwt;
        });
        Timestamp now = Timestamp.from(Instant.now());
        UUID vendorId = TestParents.vendor(jdbc, tenant);
        orderId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, status, subtotal, fees, taxes, discounts,
                                    discount_total, total, checkout_idempotency_key, delivery_confirmation_code_hash,
                                    delivery_confirmation_code_display, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'PAYMENT_CONFIRMED', 90, 10, 0, 0, 0, 100, ?, 'hash', '000000', ?, ?)""",
                orderId, tenant, "ref-" + orderId, TestParents.customer(jdbc, tenant), vendorId, "key-" + orderId, now, now);
        merchant = "kc-" + UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'Merchant', 'ACTIVE', ?, ?)",
                profileId, tenant, merchant, merchant + "@example.test", now, now);
        jdbc.update("INSERT INTO app_user_profile_roles (app_user_profile_id, role) VALUES (?, 'VENDOR_ADMIN')", profileId);
        jdbc.update("INSERT INTO merchant_users (id, tenant_id, vendor_id, app_user_profile_id, role) VALUES (?, ?, ?, ?, 'OWNER')",
                UUID.randomUUID(), tenant, vendorId, profileId);
    }

    @Test
    void acceptPersistsThePublicationAndCompletesItAfterTheListenersRun() throws Exception {
        double before = transitions();

        accept();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            List<Map<String, Object>> rows = publications();
            assertThat(rows).hasSize(3); // one per listener (metrics, notifications, test), written in the accept transaction
            assertThat(rows).allSatisfy(row -> assertThat(row.get("completion_date")).isNotNull());
        });
        assertThat(publications()).extracting(row -> row.get("event_type"))
                .containsOnly(OrderStatusChanged.class.getName());
        assertThat(transitions() - before).isEqualTo(1.0);
        assertThat(FlakyListener.attempts.get(orderId).get()).isEqualTo(1);
        // LAC-48: OrderStatusNotificationListener queued the customer's "order accepted" push (outbox row).
        assertThat(jdbc.queryForList("SELECT channel, status FROM notification_deliveries WHERE idempotency_key LIKE ?",
                "order-status:" + orderId + ":ACCEPTED_BY_VENDOR:%"))
                .singleElement().satisfies(row -> assertThat(row).containsEntry("channel", "PUSH").containsEntry("status", "QUEUED"));
    }

    @Test
    void aFailingListenerLeavesItsPublicationIncompleteAndTheRetryJobRedeliversIt() throws Exception {
        FlakyListener.failuresRemaining.set(1);

        accept();

        // The healthy listener completes; the failing one stays incomplete after its attempt.
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(FlakyListener.attempts.get(orderId)).isNotNull();
            assertThat(FlakyListener.attempts.get(orderId).get()).isEqualTo(1);
            assertThat(completion(METRICS_LISTENER)).isNotNull();
        });
        assertThat(publications()).filteredOn(row -> ((String) row.get("listener_id")).contains("FlakyListener"))
                .singleElement().satisfies(row -> assertThat(row.get("completion_date")).isNull());

        retryJob.run();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(publications()).allSatisfy(row -> assertThat(row.get("completion_date")).isNotNull()));
        assertThat(FlakyListener.attempts.get(orderId).get()).isEqualTo(2);
        assertThat(publications()).hasSize(3); // redelivery reuses the row; the completed listeners are not re-run
    }

    // --- helpers ---

    private void accept() throws Exception {
        mockMvc.perform(patch("/api/v1/vendor/orders/{id}/accept", orderId).header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED_BY_VENDOR"));
    }

    private List<Map<String, Object>> publications() {
        return jdbc.queryForList("SELECT listener_id, event_type, completion_date FROM event_publication WHERE serialized_event LIKE ?",
                "%" + orderId + "%");
    }

    private Object completion(String listenerId) {
        return publications().stream().filter(row -> ((String) row.get("listener_id")).contains(listenerId))
                .findFirst().map(row -> row.get("completion_date")).orElse(null);
    }

    private double transitions() {
        var counter = meterRegistry.find("order.status.transitions").tags("from", "PAYMENT_CONFIRMED", "to", "ACCEPTED_BY_VENDOR").counter();
        return counter == null ? 0 : counter.count();
    }

    private String token() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", merchant);
        claims.put("azp", "delivery-app");
        claims.put("tenant_id", tenant.toString());
        claims.put("realm_access", Map.of("roles", List.of("VENDOR_ADMIN")));
        String value = "event-token-" + UUID.randomUUID();
        tokens.put(value, new Jwt(value, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "RS256"), claims));
        return value;
    }

    /** Registers the test-only listener below. */
    @TestConfiguration
    static class FlakyListenerConfig {
        @org.springframework.context.annotation.Bean
        FlakyListener flakyListener() {
            return new FlakyListener();
        }
    }

    /** Second listener of the event that throws while {@code failuresRemaining > 0}, counting attempts per order. */
    static class FlakyListener {
        static final AtomicInteger failuresRemaining = new AtomicInteger();
        static final Map<UUID, AtomicInteger> attempts = new ConcurrentHashMap<>();

        @ApplicationModuleListener
        public void on(OrderStatusChanged event) {
            attempts.computeIfAbsent(event.orderId(), id -> new AtomicInteger()).incrementAndGet();
            if (failuresRemaining.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                throw new IllegalStateException("simulated listener failure");
            }
        }
    }
}
