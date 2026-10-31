package com.delivery.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.PostgresApiE2ETestBase;
import com.delivery.common.TestParents;
import com.delivery.common.idempotency.IdempotencyCleanupJob;
import com.delivery.common.security.TenancyProperties;
import com.delivery.payment.api.PaymentProvider;
import com.delivery.payment.provider.SandboxProvider;
import com.delivery.payment.service.PaymentAttemptResolver;
import com.delivery.payment.service.PaymentReconciliationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * W7-A (LAC-31/32/24) end to end on real Postgres with the sandbox provider: attempt → signed webhook → payment
 * CONFIRMED → ledger PAYMENT_CAPTURED; duplicate/concurrent webhooks; invalid signature 401; poller resolution (incl. an
 * initiate timeout); attempt expiry and retry; a late success on a cancelled payment opening a reconciliation case;
 * the daily reconciliation; and the generic Idempotency-Key (replay, 409 reuse, concurrency).
 *
 * <p>Same properties as {@code SettlementLedgerPostgresTest} on purpose (one cached context): ledger listeners on,
 * 3-connection pool. Data lives in the operator tenant (customers resolve it from the {@code pede-aqui-web} client).
 */
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=3",
        "app.settlement.enabled=true",
        "app.payments.mock-confirmation.enabled=true"})
class PaymentAttemptsPostgresTest extends PostgresApiE2ETestBase {
    private static final String PHONE = "+258841234567";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenancyProperties tenancyProperties;
    @Autowired private SandboxProvider sandbox;
    @Autowired private PaymentAttemptResolver resolver;
    @Autowired private PaymentReconciliationService reconciliation;
    @Autowired private IdempotencyCleanupJob idempotencyCleanup;

    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
    private UUID tenant;
    private UUID vendor;
    private ExecutorService pool;

    private record Seeded(UUID orderId, UUID paymentId, UUID customerId, String token) {
    }

    @BeforeEach
    void seed() {
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            Jwt jwt = tokens.get(invocation.<String>getArgument(0));
            if (jwt == null) throw new BadJwtException("unknown test token");
            return jwt;
        });
        tenant = tenancyProperties.getOperatorTenantId();
        vendor = TestParents.vendor(jdbc, tenant);
        pool = Executors.newFixedThreadPool(6);
    }

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    // --- happy path ---------------------------------------------------------------------------------------------------

    @Test
    void attemptThenSignedWebhookConfirmsThePaymentAndPostsTheCapture() throws Exception {
        Seeded s = seedPendingOrder();
        mockMvc.perform(get("/api/v1/me/orders/{id}/payment", s.orderId()).header("Authorization", "Bearer " + s.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(s.paymentId().toString()))
                .andExpect(jsonPath("$.payable").value(true))
                .andExpect(jsonPath("$.latestAttempt").doesNotExist());

        JsonNode attempt = startAttempt(s, PHONE).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_CUSTOMER"))
                .andExpect(jsonPath("$.provider").value("sandbox"))
                .andExpect(jsonPath("$.msisdnMasked").value("*******567"))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andReturn().getResponse().getContentAsString().transform(this::json);
        UUID attemptId = UUID.fromString(attempt.get("id").asText());
        String reference = providerReference(attemptId);
        assertThat(jdbc.queryForObject("SELECT msisdn_hash FROM payment_attempts WHERE id = ?", String.class, attemptId))
                .hasSize(64).doesNotContain("841234567");

        webhook(sandbox.callbackFor(reference, PaymentProvider.Outcome.SUCCEEDED, "evt-" + UUID.randomUUID()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("processed"));

        assertThat(attemptStatus(attemptId)).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForMap("SELECT status, provider FROM payments WHERE id = ?", s.paymentId()))
                .containsEntry("status", "CONFIRMED").containsEntry("provider", "SANDBOX");
        assertThat(orderStatus(s)).isEqualTo("PAYMENT_CONFIRMED");
        assertThat(jdbc.queryForMap("SELECT reason, actor_type, actor_id FROM payment_status_history WHERE payment_id = ? AND to_status = 'CONFIRMED'",
                s.paymentId())).containsEntry("reason", "PSP_CONFIRMATION").containsEntry("actor_type", "SYSTEM")
                .containsEntry("actor_id", "psp:sandbox");
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE target_id = ? AND action = 'PAYMENT_CONFIRMED'", s.paymentId().toString())).isEqualTo(1);
        await().atMost(Duration.ofSeconds(20)).until(() ->
                count("SELECT COUNT(*) FROM ledger_transactions WHERE tenant_id = ? AND idempotency_key = ?", tenant,
                        "payment-captured:" + s.paymentId()) == 1);

        mockMvc.perform(get("/api/v1/payments/{p}/attempts/{a}", s.paymentId(), attemptId).header("Authorization", "Bearer " + s.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mockMvc.perform(get("/api/v1/me/orders/{id}/payment", s.orderId()).header("Authorization", "Bearer " + s.token()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.payable").value(false))
                .andExpect(jsonPath("$.latestAttempt.status").value("SUCCEEDED"));
        // Paid: no new attempt.
        startAttempt(s, PHONE).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("payment_not_payable"));
    }

    @Test
    void otherCustomersAndBadInputAreRefused() throws Exception {
        Seeded s = seedPendingOrder();
        String stranger = customerToken(TestParents.customer(jdbc, tenant));
        mockMvc.perform(post("/api/v1/payments/{id}/attempts", s.paymentId()).header("Authorization", "Bearer " + stranger)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"msisdn\":\"" + PHONE + "\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/me/orders/{id}/payment", s.orderId()).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
        startAttempt(s, "12345").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_msisdn"));
        mockMvc.perform(post("/api/v1/payments/{id}/attempts", s.paymentId()).header("Authorization", "Bearer " + staffToken("OPS"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"msisdn\":\"" + PHONE + "\"}"))
                .andExpect(status().isForbidden());
        assertThat(count("SELECT COUNT(*) FROM payment_attempts WHERE payment_id = ?", s.paymentId())).isZero();
    }

    // --- webhooks -----------------------------------------------------------------------------------------------------

    @Test
    void duplicateAndConcurrentWebhooksApplyOnceAndInvalidSignaturesAre401() throws Exception {
        Seeded s = seedPendingOrder();
        UUID attemptId = attemptId(startAttempt(s, PHONE).andExpect(status().isAccepted()));
        String reference = providerReference(attemptId);

        SandboxProvider.SignedCallback forged = sandbox.callbackFor(reference, PaymentProvider.Outcome.SUCCEEDED, "evt-forged");
        byte[] tampered = new String(forged.body(), java.nio.charset.StandardCharsets.UTF_8).replace("\"evt-forged\"", "\"evt-forged2\"")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        mockMvc.perform(post("/api/v1/payments/webhooks/sandbox").contentType(MediaType.APPLICATION_JSON)
                        .header(SandboxProvider.SIGNATURE_HEADER, forged.signatureHeader())
                        .content(tampered))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("invalid_webhook_signature"));
        mockMvc.perform(post("/api/v1/payments/webhooks/sandbox").contentType(MediaType.APPLICATION_JSON).content(forged.body()))
                .andExpect(status().isUnauthorized());
        assertThat(attemptStatus(attemptId)).isEqualTo("PENDING_CUSTOMER");
        assertThat(count("SELECT COUNT(*) FROM payment_webhook_events WHERE payload_sha256 = ? AND NOT signature_valid AND outcome = 'INVALID_SIGNATURE'",
                sha256(tampered))).isEqualTo(1);
        mockMvc.perform(post("/api/v1/payments/webhooks/unknown-psp").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("payment_provider_not_found"));

        String eventId = "evt-" + UUID.randomUUID();
        SandboxProvider.SignedCallback callback = sandbox.callbackFor(reference, PaymentProvider.Outcome.SUCCEEDED, eventId);
        List<String> results = concurrently(5, () -> json(webhook(callback).andReturn().getResponse().getContentAsString()).get("result").asText());
        assertThat(results).containsOnlyOnce("processed").allMatch(r -> r.equals("processed") || r.equals("duplicate"));
        webhook(callback).andExpect(jsonPath("$.result").value("duplicate"));
        // Another event id with the same (already applied) outcome: recorded, no second effect.
        webhook(sandbox.callbackFor(reference, PaymentProvider.Outcome.SUCCEEDED, "evt-" + UUID.randomUUID()))
                .andExpect(jsonPath("$.result").value("ignored"));
        // A FAILED arriving after the success never undoes it.
        webhook(sandbox.callbackFor(reference, PaymentProvider.Outcome.FAILED, "evt-" + UUID.randomUUID()))
                .andExpect(jsonPath("$.result").value("ignored"));

        assertThat(attemptStatus(attemptId)).isEqualTo("SUCCEEDED");
        assertThat(count("SELECT COUNT(*) FROM payment_status_history WHERE payment_id = ? AND to_status = 'CONFIRMED'", s.paymentId())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM order_status_history WHERE order_id = ? AND to_status = 'PAYMENT_CONFIRMED'", s.orderId())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM payment_webhook_events WHERE provider_event_id = ? AND signature_valid", eventId)).isEqualTo(1);
        webhook(sandbox.signedCallback("evt-" + UUID.randomUUID(), "SBX-nope", "PA-nope", PaymentProvider.Outcome.SUCCEEDED, null))
                .andExpect(jsonPath("$.result").value("unmatched"));
    }

    @Test
    void failedAttemptKeepsTheOrderPendingAndTheCustomerCanRetry() throws Exception {
        Seeded s = seedPendingOrder();
        UUID first = attemptId(startAttempt(s, PHONE));
        startAttempt(s, PHONE).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("payment_attempt_in_progress"));

        webhook(sandbox.callbackFor(providerReference(first), PaymentProvider.Outcome.FAILED, "evt-" + UUID.randomUUID()))
                .andExpect(jsonPath("$.result").value("processed"));
        assertThat(jdbc.queryForMap("SELECT status, error_code FROM payment_attempts WHERE id = ?", first))
                .containsEntry("status", "FAILED").containsEntry("error_code", "INSUFFICIENT_BALANCE");
        assertThat(orderStatus(s)).isEqualTo("PAYMENT_PENDING");
        assertThat(paymentStatus(s)).isEqualTo("PENDING_CONFIRMATION");

        UUID second = attemptId(startAttempt(s, PHONE).andExpect(status().isAccepted()));
        assertThat(second).isNotEqualTo(first);
    }

    // --- poller / expiry ----------------------------------------------------------------------------------------------

    @Test
    void pollerResolvesWithoutCallbackIncludingAnInitiateTimeout() throws Exception {
        Seeded s = seedPendingOrder();
        UUID attemptId = attemptId(startAttempt(s, PHONE));
        assertThat(resolver.pollOne(attemptId)).isEqualTo(PaymentAttemptResolver.Outcome.RESCHEDULED); // customer still deciding
        assertThat(jdbc.queryForObject("SELECT poll_count FROM payment_attempts WHERE id = ?", Integer.class, attemptId)).isEqualTo(1);
        sandbox.settle(providerReference(attemptId), PaymentProvider.Outcome.SUCCEEDED);
        assertThat(resolver.pollOne(attemptId)).isEqualTo(PaymentAttemptResolver.Outcome.RESOLVED);
        assertThat(attemptStatus(attemptId)).isEqualTo("SUCCEEDED");
        assertThat(paymentStatus(s)).isEqualTo("CONFIRMED");
        assertThat(orderStatus(s)).isEqualTo("PAYMENT_CONFIRMED");

        // Phone ending 0003: the initiate call times out (never a failure) although the PSP charges the customer.
        Seeded t = seedPendingOrder();
        UUID timedOut = attemptId(startAttempt(t, "+258840000003").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("UNKNOWN")));
        startAttempt(t, PHONE).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("payment_attempt_in_progress"));
        jdbc.update("UPDATE payment_attempts SET next_poll_at = now() - interval '1 second' WHERE id = ?", timedOut);
        // Find the simulated charge by our reference (the provider reference never reached us) and let it succeed.
        PaymentProvider.StatusResult psp = sandbox.query(null, jdbc.queryForObject("SELECT our_reference FROM payment_attempts WHERE id = ?", String.class, timedOut));
        sandbox.settle(psp.providerReference(), PaymentProvider.Outcome.SUCCEEDED);
        assertThat(resolver.pollDue().resolved()).isGreaterThanOrEqualTo(1);
        assertThat(attemptStatus(timedOut)).isEqualTo("SUCCEEDED");
        assertThat(providerReference(timedOut)).isEqualTo(psp.providerReference());
        assertThat(paymentStatus(t)).isEqualTo("CONFIRMED");
    }

    @Test
    void unansweredAttemptExpiresAndTheCustomerRetries() throws Exception {
        Seeded s = seedPendingOrder();
        UUID attemptId = attemptId(startAttempt(s, "+258840000002")); // customer never answers
        assertThat(resolver.expireOne(attemptId)).isEqualTo(PaymentAttemptResolver.Outcome.RESOLVED);
        assertThat(attemptStatus(attemptId)).isEqualTo("EXPIRED"); // expireOne ignores the age; the job selects by TTL
        assertThat(resolver.expireOne(attemptId)).isEqualTo(PaymentAttemptResolver.Outcome.ALREADY_SETTLED);
        assertThat(orderStatus(s)).isEqualTo("PAYMENT_PENDING");
        assertThat(paymentStatus(s)).isEqualTo("PENDING_CONFIRMATION");

        UUID retry = attemptId(startAttempt(s, PHONE).andExpect(status().isAccepted()));
        jdbc.update("UPDATE payment_attempts SET created_at = now() - interval '10 minutes' WHERE id = ?", retry);
        assertThat(resolver.expireStale().resolved()).isGreaterThanOrEqualTo(1);
        assertThat(attemptStatus(retry)).isEqualTo("EXPIRED");
    }

    // --- reconciliation -----------------------------------------------------------------------------------------------

    @Test
    void lateSuccessOnACancelledPaymentOpensACaseThatFinanceListsAndResolves() throws Exception {
        Seeded s = seedPendingOrder();
        UUID attemptId = attemptId(startAttempt(s, PHONE));
        jdbc.update("UPDATE payment_attempts SET status = 'EXPIRED', completed_at = now(), next_poll_at = NULL WHERE id = ?", attemptId);
        jdbc.update("UPDATE payments SET status = 'CANCELLED' WHERE id = ?", s.paymentId());
        jdbc.update("UPDATE orders SET status = 'CANCELLED', cancellation_reason = 'PAYMENT_EXPIRED' WHERE id = ?", s.orderId());

        webhook(sandbox.callbackFor(providerReference(attemptId), PaymentProvider.Outcome.SUCCEEDED, "evt-" + UUID.randomUUID()))
                .andExpect(jsonPath("$.result").value("processed"));
        assertThat(attemptStatus(attemptId)).isEqualTo("SUCCEEDED"); // the money was taken: recorded truthfully
        assertThat(paymentStatus(s)).isEqualTo("CANCELLED");
        assertThat(orderStatus(s)).isEqualTo("CANCELLED");
        Map<String, Object> row = jdbc.queryForMap("SELECT id, case_type, status, detail_code FROM payment_reconciliation_cases WHERE attempt_id = ?", attemptId);
        assertThat(row).containsEntry("case_type", "SUCCEEDED_NOT_CAPTURED").containsEntry("status", "OPEN")
                .containsEntry("detail_code", "payment_cancelled");

        // The daily run finds the same mismatch but does not duplicate the case.
        reconciliation.runDaily();
        assertThat(count("SELECT COUNT(*) FROM payment_reconciliation_cases WHERE attempt_id = ?", attemptId)).isEqualTo(1);

        String finance = staffToken("FINANCE");
        String caseId = row.get("id").toString();
        mockMvc.perform(get("/api/v1/payments/reconciliation-cases").param("status", "OPEN").param("size", "100")
                        .header("Authorization", "Bearer " + finance))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + caseId + "')].caseType").value("SUCCEEDED_NOT_CAPTURED"));
        mockMvc.perform(get("/api/v1/payments/reconciliation-cases").header("Authorization", "Bearer " + s.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/payments/reconciliation-cases/{id}/resolve", caseId).header("Authorization", "Bearer " + finance)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Reembolso manual no portal do PSP\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE target_id = ? AND action = 'PAYMENT_RECONCILIATION_CASE_RESOLVED'", caseId)).isEqualTo(1);
    }

    @Test
    void dailyReconciliationFlagsAmountMismatchesAndUnresolvedAttemptsIdempotently() throws Exception {
        Seeded s = seedPendingOrder();
        UUID mismatch = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO payment_attempts (id, tenant_id, payment_id, order_id, provider, our_reference, provider_reference, status,
                                              amount, currency, created_at, updated_at, completed_at)
                VALUES (?, ?, ?, ?, 'sandbox', ?, ?, 'SUCCEEDED', 99.00, 'MZN', now(), now(), now())""",
                mismatch, tenant, s.paymentId(), s.orderId(), "PA" + mismatch.toString().substring(0, 18), "SBX-" + mismatch);
        Seeded u = seedPendingOrder();
        UUID unknown = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO payment_attempts (id, tenant_id, payment_id, order_id, provider, our_reference, status, amount, currency,
                                              created_at, updated_at, next_poll_at)
                VALUES (?, ?, ?, ?, 'sandbox', ?, 'UNKNOWN', 100.00, 'MZN', now() - interval '2 hours', now(), now() + interval '1 hour')""",
                unknown, tenant, u.paymentId(), u.orderId(), "PA" + unknown.toString().substring(0, 18));

        PaymentReconciliationService.RunResult first = reconciliation.runDaily();
        assertThat(first.opened()).isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT case_type FROM payment_reconciliation_cases WHERE attempt_id = ?", String.class, mismatch))
                .isEqualTo("AMOUNT_MISMATCH");
        assertThat(jdbc.queryForObject("SELECT case_type FROM payment_reconciliation_cases WHERE attempt_id = ?", String.class, unknown))
                .isEqualTo("ATTEMPT_UNRESOLVED");
        reconciliation.runDaily();
        assertThat(count("SELECT COUNT(*) FROM payment_reconciliation_cases WHERE attempt_id IN (?, ?)", mismatch, unknown)).isEqualTo(2);
    }

    // --- Idempotency-Key (LAC-24) -------------------------------------------------------------------------------------

    @Test
    void idempotencyKeyReplaysTheFirstResponseAndRejectsADifferentRequest() throws Exception {
        Seeded s = seedPendingOrder();
        String key = "pay-" + UUID.randomUUID();
        String first = startAttempt(s, PHONE, key).andExpect(status().isAccepted())
                .andExpect(header().doesNotExist("Idempotency-Replayed")).andReturn().getResponse().getContentAsString();
        String second = startAttempt(s, PHONE, key).andExpect(status().isAccepted())
                .andExpect(header().string("Idempotency-Replayed", "true")).andReturn().getResponse().getContentAsString();
        assertThat(json(second)).isEqualTo(json(first));
        assertThat(count("SELECT COUNT(*) FROM payment_attempts WHERE payment_id = ?", s.paymentId())).isEqualTo(1);

        startAttempt(s, "+258849999999", key).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("idempotency_key_reused"));
        startAttempt(s, PHONE, "bad key!").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_idempotency_key"));
        // Without a key the domain rule still answers (an attempt is open).
        startAttempt(s, PHONE).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("payment_attempt_in_progress"));
        // A non-2xx outcome is not stored: the key stays usable.
        String other = "pay-" + UUID.randomUUID();
        startAttempt(s, PHONE, other).andExpect(status().isConflict());
        assertThat(count("SELECT COUNT(*) FROM idempotency_records WHERE idempotency_key = ?", other)).isZero();

        jdbc.update("UPDATE idempotency_records SET expires_at = now() - interval '1 minute' WHERE idempotency_key = ?", key);
        assertThat(idempotencyCleanup.purgeExpired()).isGreaterThanOrEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM idempotency_records WHERE idempotency_key = ?", key)).isZero();
    }

    @Test
    void concurrentRequestsWithOneIdempotencyKeyCreateOneAttempt() throws Exception {
        Seeded s = seedPendingOrder();
        String key = "pay-" + UUID.randomUUID();
        List<Integer> statuses = concurrently(6, () -> startAttempt(s, PHONE, key).andReturn().getResponse().getStatus());
        assertThat(statuses).contains(202).allMatch(code -> code == 202 || code == 409);
        assertThat(count("SELECT COUNT(*) FROM payment_attempts WHERE payment_id = ?", s.paymentId())).isEqualTo(1);
        // Once settled, every repeat is a replay of the single attempt.
        startAttempt(s, PHONE, key).andExpect(status().isAccepted()).andExpect(header().string("Idempotency-Replayed", "true"));
    }

    // --- helpers ------------------------------------------------------------------------------------------------------

    private Seeded seedPendingOrder() {
        Timestamp now = Timestamp.from(Instant.now());
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID customerId = TestParents.customer(jdbc, tenant);
        jdbc.update("""
                INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, status, subtotal, fees, taxes, discounts,
                                    discount_total, total, checkout_idempotency_key, delivery_confirmation_code_hash, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'PAYMENT_PENDING', 90, 10, 0, 0, 0, 100, ?, 'hash', ?, ?)""",
                orderId, tenant, "ref-" + orderId, customerId, vendor, "key-" + orderId, now, now);
        jdbc.update("""
                INSERT INTO payments (id, tenant_id, order_id, amount, provider, idempotency_key, status, created_at, updated_at)
                VALUES (?, ?, ?, 100.00, 'LOCAL_MOCK', ?, 'PENDING_CONFIRMATION', ?, ?)""",
                paymentId, tenant, orderId, "key-" + orderId, now, now);
        return new Seeded(orderId, paymentId, customerId, customerToken(customerId));
    }

    private ResultActions startAttempt(Seeded s, String msisdn) throws Exception {
        return startAttempt(s, msisdn, null);
    }

    private ResultActions startAttempt(Seeded s, String msisdn, String idempotencyKey) throws Exception {
        var request = post("/api/v1/payments/{id}/attempts", s.paymentId()).header("Authorization", "Bearer " + s.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"msisdn\":\"" + msisdn + "\"}");
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request);
    }

    private ResultActions webhook(SandboxProvider.SignedCallback callback) throws Exception {
        return mockMvc.perform(post("/api/v1/payments/webhooks/sandbox").contentType(MediaType.APPLICATION_JSON)
                .header(SandboxProvider.SIGNATURE_HEADER, callback.signatureHeader()).content(callback.body()));
    }

    private UUID attemptId(ResultActions result) throws Exception {
        return UUID.fromString(json(result.andReturn().getResponse().getContentAsString()).get("id").asText());
    }

    private String providerReference(UUID attemptId) {
        return jdbc.queryForObject("SELECT provider_reference FROM payment_attempts WHERE id = ?", String.class, attemptId);
    }

    private String attemptStatus(UUID attemptId) {
        return jdbc.queryForObject("SELECT status FROM payment_attempts WHERE id = ?", String.class, attemptId);
    }

    private String paymentStatus(Seeded s) {
        return jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, s.paymentId());
    }

    private String orderStatus(Seeded s) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, s.orderId());
    }

    private static String sha256(byte[] body) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(body));
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private JsonNode json(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> List<T> concurrently(int threads, Callable<T> body) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return body.call();
            }));
        }
        start.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(future.get(60, TimeUnit.SECONDS));
        }
        return results;
    }

    private String staffToken(String role) {
        return token("kc-staff-" + UUID.randomUUID(), role, Map.of("tenant_id", tenant.toString()));
    }

    /** Customers carry no tenant_id claim: the tenant comes from the OIDC client (azp). */
    private String customerToken(UUID customerProfileId) {
        return token("kc-customer-" + customerProfileId, "CUSTOMER", Map.of("azp", "pede-aqui-web"));
    }

    private String token(String subject, String role, Map<String, Object> extraClaims) {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        claims.put("sub", subject);
        claims.putIfAbsent("azp", "delivery-app");
        claims.put("realm_access", Map.of("roles", List.of(role)));
        String value = "w7a-token-" + UUID.randomUUID();
        tokens.put(value, new Jwt(value, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "RS256"), claims));
        return value;
    }

}
