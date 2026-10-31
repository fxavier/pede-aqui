package com.delivery.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.PostgresApiE2ETestBase;
import com.delivery.common.TestParents;
import com.delivery.settlement.entity.LedgerAccountType;
import com.delivery.settlement.repository.LedgerRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * LAC-44 (W7-B) end to end on real Postgres: the courier reports FAILED_DELIVERY with a coded reason (order CANCELLED
 * with DELIVERY_FAILED, OPEN incident with the policy default and arrival evidence, customer notification queued,
 * audited), OPS resolve it (refund requested, notification queued, audited, {@code DeliveryIncidentResolved} → ledger
 * posting of the cost bearer), overrides need a reason and are audited, resolution is idempotent, two operators
 * resolving at once produce exactly one effect, and the pre-pickup STORE_FAULT path.
 *
 * <p>Same context properties as {@code SettlementLedgerPostgresTest} (ledger listeners on, 3-connection pool), so the
 * two share one cached application context. Every test seeds a fresh tenant (ledger/audit rows are immutable).
 */
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=3",
        "app.settlement.enabled=true",
        "app.payments.mock-confirmation.enabled=true"})
class DeliveryIncidentPostgresTest extends PostgresApiE2ETestBase {
    // Maputo; the courier's last position is ~110 m north of the address.
    private static final double ADDRESS_LAT = -25.9692;
    private static final double ADDRESS_LNG = 32.5732;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LedgerRepository ledger;

    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
    private UUID tenant;
    private UUID vendor;
    private UUID customer;
    private UUID courier;
    private String courierToken;
    private String opsToken;
    private ExecutorService pool;

    @BeforeEach
    void seed() {
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            Jwt jwt = tokens.get(invocation.<String>getArgument(0));
            if (jwt == null) throw new BadJwtException("unknown test token");
            return jwt;
        });
        tenant = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, 'Incidents', ?, 'ACTIVE', 'MZN', ?, ?)",
                tenant, "incidents-" + tenant, now, now);
        vendor = TestParents.vendor(jdbc, tenant);
        customer = TestParents.customer(jdbc, tenant);
        String courierSubject = "kc-courier-" + UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'Courier', 'ACTIVE', ?, ?)",
                profile, tenant, courierSubject, courierSubject + "@test.local", now, now);
        courier = UUID.randomUUID();
        jdbc.update("INSERT INTO couriers (id, tenant_id, user_profile_id, verification_status, status, created_at, updated_at) VALUES (?, ?, ?, 'APPROVED', 'BUSY', ?, ?)",
                courier, tenant, profile, now, now);
        jdbc.update("""
                INSERT INTO courier_last_locations (courier_id, tenant_id, location, latitude, longitude, accuracy_m, recorded_at, received_at)
                VALUES (?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, 12, ?, ?)""",
                courier, tenant, ADDRESS_LNG, ADDRESS_LAT + 0.001, ADDRESS_LAT + 0.001, ADDRESS_LNG, now, now);
        courierToken = jwt(courierSubject, "COURIER");
        opsToken = jwt("kc-ops-" + UUID.randomUUID(), "OPS");
        pool = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    // --- per reason: default outcome, refund, notifications, ledger effect ---

    @ParameterizedTest
    @CsvSource({
            // reason,           bearer,   outcome,                   goods,           refund, retained, charge, compensation
            "CUSTOMER_ABSENT,    CUSTOMER, REFUND_MINUS_DELIVERY_FEE, RETURN_TO_STORE, 103.00, 7.00,  0.00,  0.00",
            "WRONG_ADDRESS,      CUSTOMER, REFUND_MINUS_DELIVERY_FEE, RETURN_TO_STORE, 103.00, 7.00,  0.00,  0.00",
            "CUSTOMER_REFUSED,   CUSTOMER, REFUND_MINUS_DELIVERY_FEE, RETURN_TO_STORE, 103.00, 7.00,  0.00,  0.00",
            "STORE_FAULT,        MERCHANT, REFUND_FULL,               RETURN_TO_STORE, 110.00, 0.00, 10.00,  0.00",
            "DAMAGED_IN_TRANSIT, PLATFORM, REFUND_FULL,               DISCARDED,       110.00, 0.00,  0.00, 100.00",
            "ACCIDENT,           PLATFORM, REFUND_FULL,               DISCARDED,       110.00, 0.00,  0.00, 100.00",
            "OTHER,              PLATFORM, REFUND_FULL,               RETURN_TO_STORE, 110.00, 0.00,  0.00,  0.00"})
    void failureAndDefaultResolutionPerReason(String reason, String bearer, String outcome, String goods, String refund,
                                              String retained, String charge, String compensation) throws Exception {
        UUID order = order("DELIVERING", "COMMITTED");
        UUID delivery = delivery(order, "ARRIVED_AT_CUSTOMER");

        fail(delivery, reason).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED_DELIVERY"));

        // Order: cancelled as a failed delivery (consistent with pre-W7-B behaviour), no refund yet.
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, order)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT cancellation_reason FROM orders WHERE id = ?", String.class, order)).isEqualTo("DELIVERY_FAILED");
        assertThat(count("SELECT COUNT(*) FROM refunds WHERE order_id = ?", order)).isZero();
        // Incident: OPEN with the policy default and arrival evidence.
        Map<String, Object> incident = jdbc.queryForMap("SELECT * FROM delivery_incidents WHERE delivery_id = ?", delivery);
        UUID incidentId = (UUID) incident.get("id");
        assertThat(incident.get("status")).isEqualTo("OPEN");
        assertThat(incident.get("reason")).isEqualTo(reason);
        assertThat(incident.get("reported_by_role")).isEqualTo("COURIER");
        assertThat(incident.get("failed_from_status")).isEqualTo("ARRIVED_AT_CUSTOMER");
        assertThat(incident.get("default_cost_bearer")).isEqualTo(bearer);
        assertThat(incident.get("default_outcome")).isEqualTo(outcome);
        assertThat(incident.get("default_goods_disposition")).isEqualTo(goods);
        assertThat((Double) incident.get("evidence_distance_m")).isBetween(100d, 120d);
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE tenant_id = ? AND action = 'DELIVERY_INCIDENT_OPENED' AND target_id = ?",
                tenant, incidentId.toString())).isEqualTo(1);
        assertThat(notification("delivery_failed", "PUSH")).isEqualTo("QUEUED");
        assertThat(notification("delivery_failed", "IN_APP")).isEqualTo("SENT");
        // The courier is freed (LAC-42 hook still runs).
        assertThat(jdbc.queryForObject("SELECT status FROM couriers WHERE id = ?", String.class, courier)).isEqualTo("AVAILABLE");

        mockMvc.perform(get("/api/v1/ops/delivery-incidents").header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(incidentId.toString()))
                .andExpect(jsonPath("$.content[0].defaultDecision.costBearer").value(bearer))
                .andExpect(jsonPath("$.content[0].evidence.latitude").exists());

        resolve(incidentId, "{}", opsToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution.decision.costBearer").value(bearer))
                .andExpect(jsonPath("$.resolution.overridden").value(false))
                .andExpect(jsonPath("$.resolution.refundAmount").value(Double.parseDouble(refund)))
                .andExpect(jsonPath("$.resolution.retainedFeeAmount").value(Double.parseDouble(retained)))
                .andExpect(jsonPath("$.resolution.merchantChargeAmount").value(Double.parseDouble(charge)))
                .andExpect(jsonPath("$.resolution.platformCompensationAmount").value(Double.parseDouble(compensation)));

        assertThat(jdbc.queryForObject("SELECT amount FROM refunds WHERE order_id = ? AND idempotency_key = ?", BigDecimal.class,
                order, "delivery-incident-" + incidentId)).isEqualByComparingTo(refund);
        assertThat(jdbc.queryForObject("SELECT status FROM refunds WHERE order_id = ?", String.class, order)).isEqualTo("REQUESTED");
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, order)).isEqualTo("CANCELLED");
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE tenant_id = ? AND action = 'DELIVERY_INCIDENT_RESOLVED' AND result = 'SUCCESS'",
                tenant)).isEqualTo(1);
        String resolvedTemplate = new BigDecimal(retained).signum() > 0 ? "delivery_incident_refund_partial" : "delivery_incident_refund_full";
        assertThat(notification(resolvedTemplate, "PUSH")).isEqualTo("QUEUED");

        // Ledger (after commit, async): capture is posted first, then the cost-bearer transaction when it has lines.
        await().atMost(Duration.ofSeconds(20)).until(() -> transactions("PAYMENT_CAPTURED") == 1
                && event("DeliveryIncidentResolved") == 0);
        boolean anyEffect = new BigDecimal(retained).signum() > 0 || new BigDecimal(charge).signum() > 0
                || new BigDecimal(compensation).signum() > 0;
        assertThat(transactions("INCIDENT_SETTLED")).isEqualTo(anyEffect ? 1 : 0);
        assertThat(balance(LedgerAccountType.CUSTOMER_FUNDS_HELD)).isEqualByComparingTo(new BigDecimal("110.00").subtract(new BigDecimal(retained)));
        assertThat(balance(LedgerAccountType.PLATFORM_REVENUE_FEES)).isEqualByComparingTo(new BigDecimal(retained).add(new BigDecimal(charge)));
        assertThat(balance(LedgerAccountType.PLATFORM_INCIDENT_EXPENSE)).isEqualByComparingTo(compensation);
        assertThat(merchantBalance()).isEqualByComparingTo(new BigDecimal(compensation).subtract(new BigDecimal(charge)));
        assertTenantBalanced();
    }

    // --- override, idempotency, concurrency, authorization ---

    @Test
    void anOverrideNeedsAReasonAndIsAudited() throws Exception {
        UUID incidentId = failedIncident("CUSTOMER_ABSENT");
        String override = "{\"costBearer\":\"PLATFORM\",\"outcome\":\"REFUND_FULL\"}";

        resolve(incidentId, override, opsToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("override_reason_required"));
        assertThat(jdbc.queryForObject("SELECT status FROM delivery_incidents WHERE id = ?", String.class, incidentId)).isEqualTo("OPEN");

        resolve(incidentId, "{\"costBearer\":\"PLATFORM\",\"outcome\":\"REFUND_FULL\",\"overrideReason\":\"cliente provou presença\"}", opsToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution.overridden").value(true))
                .andExpect(jsonPath("$.resolution.overrideReason").value("cliente provou presença"))
                .andExpect(jsonPath("$.resolution.decision.costBearer").value("PLATFORM"))
                .andExpect(jsonPath("$.defaultDecision.costBearer").value("CUSTOMER"))
                .andExpect(jsonPath("$.resolution.refundAmount").value(110.0))
                .andExpect(jsonPath("$.resolution.retainedFeeAmount").value(0.0));

        Map<String, Object> audit = jdbc.queryForMap(
                "SELECT result, details::text AS details FROM audit_logs WHERE tenant_id = ? AND action = 'DELIVERY_INCIDENT_RESOLVED'", tenant);
        assertThat(audit.get("result")).isEqualTo("OVERRIDDEN");
        assertThat((String) audit.get("details")).contains("\"defaultCostBearer\": \"CUSTOMER\"").contains("\"costBearer\": \"PLATFORM\"")
                .doesNotContain("cliente provou");
    }

    @Test
    void resolutionIsIdempotentAndAnotherDecisionAfterwardsIsAConflict() throws Exception {
        UUID incidentId = failedIncident("STORE_FAULT");

        resolve(incidentId, "{}", opsToken).andExpect(status().isOk());
        resolve(incidentId, "{\"costBearer\":\"MERCHANT\"}", opsToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution.refundAmount").value(110.0));
        resolve(incidentId, "{\"costBearer\":\"PLATFORM\",\"overrideReason\":\"x\"}", opsToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("incident_already_resolved"));

        UUID order = jdbc.queryForObject("SELECT order_id FROM delivery_incidents WHERE id = ?", UUID.class, incidentId);
        assertThat(count("SELECT COUNT(*) FROM refunds WHERE order_id = ?", order)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE tenant_id = ? AND action = 'DELIVERY_INCIDENT_RESOLVED'", tenant)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM notification_deliveries WHERE tenant_id = ? AND template_key = 'delivery_incident_refund_full'", tenant))
                .isEqualTo(2); // one PUSH + one IN_APP, written once
    }

    @Test
    void twoOperatorsResolvingAtOnceProduceExactlyOneEffect() throws Exception {
        UUID incidentId = failedIncident("ACCIDENT");
        String otherOps = jwt("kc-ops-" + UUID.randomUUID(), "OPS");

        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (String token : List.of(opsToken, otherOps)) {
            results.add(pool.submit(() -> {
                start.await();
                return resolve(incidentId, "{}", token).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        for (Future<Integer> result : results) {
            assertThat(result.get(60, TimeUnit.SECONDS)).isEqualTo(200);
        }

        UUID order = jdbc.queryForObject("SELECT order_id FROM delivery_incidents WHERE id = ?", UUID.class, incidentId);
        assertThat(count("SELECT COUNT(*) FROM refunds WHERE order_id = ?", order)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE tenant_id = ? AND action = 'DELIVERY_INCIDENT_RESOLVED'", tenant)).isEqualTo(1);
        await().atMost(Duration.ofSeconds(20)).until(() -> transactions("INCIDENT_SETTLED") == 1);
        assertThat(merchantBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void onlyOpsAndAdminResolveAndIncidentsAreTenantScoped() throws Exception {
        UUID incidentId = failedIncident("OTHER");

        resolve(incidentId, "{}", courierToken).andExpect(status().isForbidden());
        resolve(incidentId, "{}", jwt("kc-support-" + UUID.randomUUID(), "SUPPORT")).andExpect(status().isForbidden());
        // Reads are OPS/ADMIN only: the evidence carries the courier's position (LAC-40 audience).
        mockMvc.perform(get("/api/v1/ops/delivery-incidents/{id}", incidentId)
                        .header("Authorization", "Bearer " + jwt("kc-support-" + UUID.randomUUID(), "SUPPORT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/ops/delivery-incidents/{id}", incidentId)
                        .header("Authorization", "Bearer " + jwt("kc-admin-" + UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("OTHER"));

        UUID foreignTenant = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, 'Other', ?, 'ACTIVE', 'MZN', ?, ?)",
                foreignTenant, "other-" + foreignTenant, now, now);
        String foreignOps = jwtFor(foreignTenant, "kc-ops-" + UUID.randomUUID(), "OPS");
        mockMvc.perform(get("/api/v1/ops/delivery-incidents/{id}", incidentId).header("Authorization", "Bearer " + foreignOps))
                .andExpect(status().isNotFound());
        resolve(incidentId, "{}", foreignOps).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT status FROM delivery_incidents WHERE id = ?", String.class, incidentId)).isEqualTo("OPEN");
    }

    // --- courier reporting rules ---

    @Test
    void aFailureWithoutAReasonIsRejectedWithoutEffects() throws Exception {
        UUID order = order("DELIVERING", "COMMITTED");
        UUID delivery = delivery(order, "ARRIVED_AT_CUSTOMER");

        mockMvc.perform(patch("/api/v1/deliveries/{id}/status", delivery)
                        .header("Authorization", "Bearer " + courierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FAILED_DELIVERY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("failure_reason_required"));

        assertThat(jdbc.queryForObject("SELECT status FROM deliveries WHERE id = ?", String.class, delivery)).isEqualTo("ARRIVED_AT_CUSTOMER");
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, order)).isEqualTo("DELIVERING");
        assertThat(count("SELECT COUNT(*) FROM delivery_incidents WHERE delivery_id = ?", delivery)).isZero();
    }

    @Test
    void atTheStoreOnlyAStoreFaultFailsTheDeliveryAndTheReservationIsReleased() throws Exception {
        UUID order = order("ASSIGNED_TO_COURIER", "RESERVED");
        UUID delivery = delivery(order, "ARRIVED_AT_VENDOR");

        fail(delivery, "CUSTOMER_ABSENT").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invalid_delivery_transition"));
        fail(delivery, "STORE_FAULT").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT stock_status FROM orders WHERE id = ?", String.class, order)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT failed_from_status FROM delivery_incidents WHERE delivery_id = ?", String.class, delivery))
                .isEqualTo("ARRIVED_AT_VENDOR");
        assertThat(jdbc.queryForObject("SELECT default_cost_bearer FROM delivery_incidents WHERE delivery_id = ?", String.class, delivery))
                .isEqualTo("MERCHANT");
    }

    // --- helpers ---

    private UUID failedIncident(String reason) throws Exception {
        UUID order = order("DELIVERING", "COMMITTED");
        UUID delivery = delivery(order, "ARRIVED_AT_CUSTOMER");
        fail(delivery, reason).andExpect(status().isOk());
        return jdbc.queryForObject("SELECT id FROM delivery_incidents WHERE delivery_id = ?", UUID.class, delivery);
    }

    private org.springframework.test.web.servlet.ResultActions fail(UUID delivery, String reason) throws Exception {
        return mockMvc.perform(patch("/api/v1/deliveries/{id}/status", delivery)
                .header("Authorization", "Bearer " + courierToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"FAILED_DELIVERY\",\"failureReason\":\"" + reason + "\",\"failureNote\":\"nota de teste\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions resolve(UUID incident, String body, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/ops/delivery-incidents/{id}/resolve", incident)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** Order of 110.00 = goods 100.00 + delivery fee 7.00 + service fee 3.00, with an address snapshot and a captured payment. */
    private UUID order(String status, String stockStatus) {
        UUID id = UUID.randomUUID();
        Timestamp created = Timestamp.from(Instant.now().minusSeconds(3600));
        jdbc.update("""
                INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, status, subtotal, fees, taxes, discounts,
                                    discount_total, total, checkout_idempotency_key, delivery_confirmation_code_hash, stock_status,
                                    quote_delivery_fee, quote_service_fee, delivery_line1, delivery_city, delivery_latitude,
                                    delivery_longitude, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 100.00, 10.00, 0, 0, 0, 110.00, ?, 'hash', ?, 7.00, 3.00, 'Av. Julius Nyerere', 'Maputo', ?, ?, ?, ?)""",
                id, tenant, "PA-" + id.toString().substring(0, 8), customer, vendor, status, "key-" + id, stockStatus,
                ADDRESS_LAT, ADDRESS_LNG, created, created);
        jdbc.update("""
                INSERT INTO payments (id, tenant_id, order_id, amount, provider, idempotency_key, status, confirmed_at, created_at, updated_at)
                VALUES (?, ?, ?, 110.00, 'LOCAL_MOCK', ?, 'CONFIRMED', ?, ?, ?)""",
                UUID.randomUUID(), tenant, id, "key-" + id, created, created, created);
        return id;
    }

    private UUID delivery(UUID order, String status) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO deliveries (id, tenant_id, order_id, courier_id, status, confirmation_code_hash, confirmation_attempts, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'hash', 0, ?, ?)""", id, tenant, order, courier, status, now, now);
        return id;
    }

    private String notification(String templateKey, String channel) {
        List<String> statuses = jdbc.queryForList("SELECT status FROM notification_deliveries WHERE tenant_id = ? AND recipient_profile_id = ? AND template_key = ? AND channel = ?",
                String.class, tenant, customer, templateKey, channel);
        assertThat(statuses).hasSize(1);
        return statuses.get(0);
    }

    private long event(String simpleName) {
        return count("SELECT COUNT(*) FROM event_publication WHERE completion_date IS NULL AND event_type LIKE ? AND serialized_event LIKE ?",
                "%" + simpleName, "%" + tenant + "%");
    }

    private long transactions(String type) {
        return count("SELECT COUNT(*) FROM ledger_transactions WHERE tenant_id = ? AND transaction_type = ?", tenant, type);
    }

    private BigDecimal merchantBalance() {
        LedgerRepository.AccountTotals totals = ledger.totals(tenant, LedgerAccountType.MERCHANT_PAYABLE, vendor);
        return totals.credit().subtract(totals.debit());
    }

    private BigDecimal balance(LedgerAccountType type) {
        LedgerRepository.AccountTotals totals = ledger.totals(tenant, type, null);
        return type.debitNormal() ? totals.debit().subtract(totals.credit()) : totals.credit().subtract(totals.debit());
    }

    private void assertTenantBalanced() {
        Map<String, Object> sums = jdbc.queryForMap("SELECT COALESCE(SUM(debit), 0) AS d, COALESCE(SUM(credit), 0) AS c FROM ledger_entries WHERE tenant_id = ?", tenant);
        assertThat((BigDecimal) sums.get("d")).isEqualByComparingTo((BigDecimal) sums.get("c"));
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private String jwt(String subject, String role) {
        return jwtFor(tenant, subject, role);
    }

    private String jwtFor(UUID tenantId, String subject, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        claims.put("azp", "delivery-app");
        claims.put("tenant_id", tenantId.toString());
        claims.put("realm_access", Map.of("roles", List.of(role)));
        String value = "incident-token-" + UUID.randomUUID();
        tokens.put(value, new Jwt(value, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "RS256"), claims));
        return value;
    }
}
