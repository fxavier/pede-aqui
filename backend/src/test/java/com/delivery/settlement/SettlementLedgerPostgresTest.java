package com.delivery.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.PostgresApiE2ETestBase;
import com.delivery.common.TestParents;
import com.delivery.order.api.OrderStateMachine;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.repository.OrderRepository;
import com.delivery.settlement.entity.LedgerAccountType;
import com.delivery.settlement.entity.LedgerTransactionType;
import com.delivery.settlement.repository.LedgerRepository;
import com.delivery.settlement.service.LedgerAccounts;
import com.delivery.settlement.service.LedgerPostingService;
import com.delivery.settlement.service.LedgerReconciliation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * LAC-33/34 on real Postgres: the V065 triggers (balanced at COMMIT, non-empty, append-only), idempotent postings
 * under duplicate/concurrent delivery, the end-to-end flow payment confirmed → delivered → refund through the event
 * registry with the resulting balances and endpoints, refunds before delivery + retained cancellation fee, the
 * per-vendor commission override, the fee-VAT rule for VAT-inclusive orders (W6-A) and the reconciliation check.
 *
 * <p>Own context: ledger listeners on ({@code app.settlement.enabled=true}; off for the rest of the suite), mock
 * payment confirmation on, 3-connection pool (the shared container's max_connections; concurrent callers queue on the pool). Every test seeds a fresh tenant — ledger rows are immutable and never cleaned.
 */
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=3",
        "app.settlement.enabled=true",
        "app.payments.mock-confirmation.enabled=true"})
class SettlementLedgerPostgresTest extends PostgresApiE2ETestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private LedgerPostingService postings;
    @Autowired private LedgerAccounts accounts;
    @Autowired private LedgerRepository ledger;
    @Autowired private LedgerReconciliation reconciliation;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderStateMachine stateMachine;
    @Autowired private PlatformTransactionManager transactionManager;

    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
    private UUID tenant;
    private UUID vendor;
    private UUID customer;
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
        jdbc.update("INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, 'Ledger', ?, 'ACTIVE', 'MZN', ?, ?)",
                tenant, "ledger-" + tenant, now, now);
        vendor = TestParents.vendor(jdbc, tenant);
        customer = TestParents.customer(jdbc, tenant);
        pool = Executors.newFixedThreadPool(8);
    }

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    // --- V065 database invariants ---

    @Test
    void anUnbalancedTransactionIsRejectedAtCommitAndABalancedOneCommits() {
        UUID cash = accounts.idOf(tenant, LedgerAccountType.PLATFORM_CASH_CLEARING, null);
        UUID held = accounts.idOf(tenant, LedgerAccountType.CUSTOMER_FUNDS_HELD, null);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            UUID id = header("unbalanced-" + UUID.randomUUID());
            ledger.insertEntry(tenant, id, cash, new BigDecimal("10.00"), BigDecimal.ZERO);
            ledger.insertEntry(tenant, id, held, BigDecimal.ZERO, new BigDecimal("9.99"));
        })).hasStackTraceContaining("unbalanced");

        String key = "balanced-" + UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            UUID id = header(key);
            ledger.insertEntry(tenant, id, cash, new BigDecimal("10.00"), BigDecimal.ZERO);
            ledger.insertEntry(tenant, id, held, BigDecimal.ZERO, new BigDecimal("10.00"));
        });
        assertThat(ledger.exists(tenant, key)).isTrue();
        assertThat(count("SELECT COUNT(*) FROM ledger_transactions WHERE tenant_id = ? AND idempotency_key LIKE 'unbalanced-%'", tenant)).isZero();
    }

    @Test
    void aTransactionWithoutEntriesAndAOneSidedEntryAreRejected() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> header("empty-" + UUID.randomUUID())))
                .hasStackTraceContaining("has no entries");

        UUID cash = accounts.idOf(tenant, LedgerAccountType.PLATFORM_CASH_CLEARING, null);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            UUID id = header("both-sides-" + UUID.randomUUID());
            ledger.insertEntry(tenant, id, cash, new BigDecimal("5.00"), new BigDecimal("5.00"));
        })).hasStackTraceContaining("ck_ledger_entries_one_side");
    }

    @Test
    void ledgerRowsAreAppendOnly() {
        UUID order = order("DELIVERED", "126.00", "100.00", "10.00", "16.00", "0.00", true);
        postings.recordOrderDelivered(tenant, order);
        UUID txId = jdbc.queryForObject("SELECT id FROM ledger_transactions WHERE tenant_id = ? AND transaction_type = 'ORDER_DELIVERED'", UUID.class, tenant);

        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_entries SET debit = debit + 1 WHERE transaction_id = ?", txId))
                .hasStackTraceContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM ledger_entries WHERE transaction_id = ?", txId))
                .hasStackTraceContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_transactions SET description = 'x' WHERE id = ?", txId))
                .hasStackTraceContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM ledger_transactions WHERE id = ?", txId))
                .hasStackTraceContaining("append-only");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE ledger_entries CASCADE"))
                .hasStackTraceContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_accounts SET owner_id = NULL WHERE tenant_id = ? AND account_type = 'MERCHANT_PAYABLE'", tenant))
                .hasStackTraceContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM ledger_accounts WHERE tenant_id = ?", tenant))
                .hasStackTraceContaining("immutable");
        assertThat(count("SELECT COUNT(*) FROM ledger_entries WHERE transaction_id = ?", txId)).isEqualTo(4);
    }

    // --- idempotency and concurrency ---

    @Test
    void duplicateAndConcurrentDeliveryEventsPostOnce() throws Exception {
        UUID order = order("DELIVERED", "126.00", "100.00", "10.00", "16.00", "0.00", true);

        List<Boolean> results = concurrently(8, () -> postings.recordOrderDelivered(tenant, order));
        postings.recordOrderDelivered(tenant, order); // late redelivery

        assertThat(results).containsOnlyOnce(true);
        assertThat(transactions(LedgerTransactionType.ORDER_DELIVERED)).isEqualTo(1);
        assertThat(transactions(LedgerTransactionType.PAYMENT_CAPTURED)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM commissions WHERE tenant_id = ? AND order_id = ?", tenant, order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT commission_amount FROM commissions WHERE order_id = ?", BigDecimal.class, order))
                .isEqualByComparingTo("15.00");
        assertThat(merchantBalance(vendor)).isEqualByComparingTo("101.00");
        assertThat(balance(LedgerAccountType.CUSTOMER_FUNDS_HELD)).isEqualByComparingTo("0.00");
        assertThat(balance(LedgerAccountType.PLATFORM_CASH_CLEARING)).isEqualByComparingTo("126.00");
        assertTenantBalanced();
    }

    @Test
    void concurrentDeliveriesForOneMerchantAndConcurrentRefundsOnOneOrderAllPost() throws Exception {
        UUID first = order("DELIVERED", "126.00", "100.00", "10.00", "16.00", "0.00", true);
        UUID second = order("DELIVERED", "126.00", "100.00", "10.00", "16.00", "0.00", true);

        CountDownLatch start = new CountDownLatch(1);
        Future<Boolean> a = pool.submit(() -> { start.await(); return postings.recordOrderDelivered(tenant, first); });
        Future<Boolean> b = pool.submit(() -> { start.await(); return postings.recordOrderDelivered(tenant, second); });
        start.countDown();
        assertThat(a.get(30, TimeUnit.SECONDS)).isTrue();
        assertThat(b.get(30, TimeUnit.SECONDS)).isTrue();
        assertThat(merchantBalance(vendor)).isEqualByComparingTo("202.00");
        assertThat(count("SELECT COUNT(*) FROM ledger_accounts WHERE tenant_id = ? AND account_type = 'MERCHANT_PAYABLE'", tenant)).isEqualTo(1);

        UUID payment = paymentOf(first);
        Instant afterDelivery = Instant.now().plusSeconds(1);
        UUID refundA = refund(payment, first, "26.00", "REFUNDED", afterDelivery);
        UUID refundB = refund(payment, first, "37.00", "REFUNDED", afterDelivery);
        CountDownLatch go = new CountDownLatch(1);
        Future<Boolean> ra = pool.submit(() -> { go.await();
            return postings.recordRefundApproved(tenant, refundA, payment, first, new BigDecimal("26.00"), afterDelivery); });
        Future<Boolean> rb = pool.submit(() -> { go.await();
            return postings.recordRefundApproved(tenant, refundB, payment, first, new BigDecimal("37.00"), afterDelivery); });
        go.countDown();
        assertThat(ra.get(30, TimeUnit.SECONDS)).isTrue();
        assertThat(rb.get(30, TimeUnit.SECONDS)).isTrue();

        assertThat(transactions(LedgerTransactionType.REFUND_AFTER_DELIVERY)).isEqualTo(2);
        assertThat(balance(LedgerAccountType.CUSTOMER_REFUNDS_PAYABLE)).isEqualByComparingTo("63.00");
        // 63 of 126 reversed: half of order one's 101 (50.50) comes off the merchant's 202.
        assertThat(merchantBalance(vendor)).isEqualByComparingTo("151.50");
        assertTenantBalanced();
    }

    // --- end to end through the event registry ---

    @Test
    void paymentConfirmedThenDeliveredThenPartiallyRefundedPostsThroughEventsAndBalances() throws Exception {
        UUID order = order("PAYMENT_PENDING", "126.00", "100.00", "10.00", "16.00", "0.00", false);
        UUID payment = paymentOf(order);

        mockMvc.perform(post("/api/v1/payments/{id}/confirm", payment).header("Authorization", "Bearer " + token("OPS"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"idempotencyKey\":\"key-" + order + "\"}"))
                .andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(20)).until(() -> transactions(LedgerTransactionType.PAYMENT_CAPTURED) == 1);
        assertThat(balance(LedgerAccountType.CUSTOMER_FUNDS_HELD)).isEqualByComparingTo("126.00");

        jdbc.update("UPDATE orders SET status = 'DELIVERING' WHERE id = ?", order);
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                stateMachine.transition(orderRepository.findByTenantIdAndId(tenant, order).orElseThrow(), OrderStatus.DELIVERED, "TEST"));
        await().atMost(Duration.ofSeconds(20)).until(() -> transactions(LedgerTransactionType.ORDER_DELIVERED) == 1);

        String finance = token("FINANCE");
        mockMvc.perform(get("/api/v1/finance/merchants/{id}/balance", vendor).header("Authorization", "Bearer " + finance))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(101.00))
                .andExpect(jsonPath("$.currency").value("MZN"));

        String created = mockMvc.perform(post("/api/v1/payments/{id}/refunds", payment).header("Authorization", "Bearer " + finance)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":63.00,\"reason\":\"damaged\",\"idempotencyKey\":\"r-" + order + "\"}"))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        UUID refundId = UUID.fromString(objectMapper.readTree(created).get("id").asText());
        mockMvc.perform(post("/api/v1/payments/refunds/{id}/approve", refundId).header("Authorization", "Bearer " + finance))
                .andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(20)).until(() -> transactions(LedgerTransactionType.REFUND_AFTER_DELIVERY) == 1);

        assertThat(merchantBalance(vendor)).isEqualByComparingTo("50.50");
        assertThat(balance(LedgerAccountType.PLATFORM_REVENUE_COMMISSION)).isEqualByComparingTo("7.50");
        assertThat(balance(LedgerAccountType.PLATFORM_REVENUE_FEES)).isEqualByComparingTo("5.00");
        assertThat(balance(LedgerAccountType.CUSTOMER_REFUNDS_PAYABLE)).isEqualByComparingTo("63.00");
        assertThat(balance(LedgerAccountType.CUSTOMER_FUNDS_HELD)).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject("SELECT commission_amount FROM commissions WHERE order_id = ?", BigDecimal.class, order))
                .isEqualByComparingTo("15.00"); // gross accrual; the reversal lives in the ledger
        assertTenantBalanced();
        await().atMost(Duration.ofSeconds(20)).until(() -> count("""
                SELECT COUNT(*) FROM event_publication WHERE listener_id LIKE '%SettlementEventListener%'
                   AND completion_date IS NULL AND serialized_event LIKE ?""", "%" + order + "%") == 0);

        JsonNode page = objectMapper.readTree(mockMvc.perform(get("/api/v1/finance/ledger/transactions")
                        .param("limit", "2").param("orderId", order.toString()).header("Authorization", "Bearer " + finance))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(page.get("items")).hasSize(2);
        assertThat(page.get("items").get(0).get("type").asText()).isEqualTo("REFUND_AFTER_DELIVERY");
        assertThat(page.get("nextCursor").isNull()).isFalse();
        JsonNode last = objectMapper.readTree(mockMvc.perform(get("/api/v1/finance/ledger/transactions")
                        .param("limit", "2").param("orderId", order.toString()).param("cursor", page.get("nextCursor").asText())
                        .header("Authorization", "Bearer " + finance))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(last.get("items")).hasSize(1);
        assertThat(last.get("items").get(0).get("type").asText()).isEqualTo("PAYMENT_CAPTURED");
        assertThat(last.get("items").get(0).get("entries")).hasSize(2);
        assertThat(last.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void refundBeforeDeliveryAndRetainedCancellationFeeEmptyFundsHeld() {
        UUID order = order("CANCELLED", "126.00", "100.00", "10.00", "16.00", "0.00", true);
        jdbc.update("UPDATE orders SET cancellation_reason = 'CUSTOMER_CANCELLED_WITH_FEE' WHERE id = ?", order);
        history(order, "PREPARING", "CANCELLED", Instant.now());
        jdbc.update("""
                INSERT INTO cancellation_requests (id, tenant_id, order_id, status, requested_by, decided_by, fee_rate, fee_amount,
                                                   refund_amount, requested_at, decided_at, updated_at)
                VALUES (?, ?, ?, 'APPROVED', ?, 'ops', 0.10, 10.00, 116.00, now(), now(), now())""",
                UUID.randomUUID(), tenant, order, customer);
        UUID payment = paymentOf(order);
        UUID refund = refund(payment, order, "116.00", "REFUNDED", Instant.now());

        assertThat(postings.recordRefundApproved(tenant, refund, payment, order, new BigDecimal("116.00"), Instant.now())).isTrue();
        assertThat(postings.recordCancellation(tenant, order)).isTrue();
        assertThat(postings.recordCancellation(tenant, order)).isFalse(); // redelivery

        assertThat(transactions(LedgerTransactionType.REFUND_BEFORE_DELIVERY)).isEqualTo(1);
        assertThat(transactions(LedgerTransactionType.CANCELLATION_FEE_RETAINED)).isEqualTo(1);
        assertThat(balance(LedgerAccountType.CUSTOMER_FUNDS_HELD)).isEqualByComparingTo("0.00");
        assertThat(balance(LedgerAccountType.CUSTOMER_REFUNDS_PAYABLE)).isEqualByComparingTo("116.00");
        assertThat(merchantBalance(vendor)).isEqualByComparingTo("8.50");
        assertThat(balance(LedgerAccountType.PLATFORM_REVENUE_COMMISSION)).isEqualByComparingTo("1.50");
        assertThat(jdbc.queryForObject("SELECT source FROM commissions WHERE order_id = ?", String.class, order)).isEqualTo("CANCELLATION_FEE");
        assertTenantBalanced();
    }

    @Test
    void aPartialRefundBeforeDeliveryScalesTheLaterDeliveryRecognition() {
        UUID order = order("DELIVERED", "126.00", "100.00", "10.00", "16.00", "0.00", true);
        UUID payment = paymentOf(order);
        Instant deliveredAt = jdbc.queryForObject("SELECT created_at FROM order_status_history WHERE order_id = ? AND to_status = 'DELIVERED'",
                Timestamp.class, order).toInstant();
        Instant before = deliveredAt.minusSeconds(60);
        UUID refund = refund(payment, order, "26.00", "REFUNDED", before);

        // The delivery event is processed first, the earlier refund's event later: the outcome is the same.
        postings.recordOrderDelivered(tenant, order);
        postings.recordRefundApproved(tenant, refund, payment, order, new BigDecimal("26.00"), before);

        assertThat(transactions(LedgerTransactionType.REFUND_BEFORE_DELIVERY)).isEqualTo(1);
        assertThat(balance(LedgerAccountType.CUSTOMER_FUNDS_HELD)).isEqualByComparingTo("0.00");
        assertThat(balance(LedgerAccountType.PLATFORM_REVENUE_COMMISSION)).isEqualByComparingTo("11.91");
        assertTenantBalanced();
    }

    @Test
    void aVatInclusiveOrderPostsItsStoredFeeVatAsVatPayableAndAnOlderOrderKeepsThePreviousRule() {
        // W6-A order: 18.00 goods (VAT 2.48 inside) + 5.90 fees (VAT 0.81 inside), taxes 0, captured 23.90.
        UUID inclusive = order("DELIVERED", "23.90", "18.00", "5.90", "0.00", "0.00", true);
        jdbc.update("""
                UPDATE orders SET quote_tax_inclusive = TRUE, quote_goods_vat = 2.48, quote_fees_vat = 0.81,
                                  quote_vat_total = 3.29, quote_net_total = 20.61
                 WHERE id = ?""", inclusive);
        UUID older = order("DELIVERED", "126.00", "100.00", "10.00", "16.00", "0.00", true); // pre-W6-A: no quote VAT

        postings.recordOrderDelivered(tenant, inclusive);
        assertThat(balance(LedgerAccountType.PLATFORM_VAT_PAYABLE)).isEqualByComparingTo("0.81");
        assertThat(balance(LedgerAccountType.PLATFORM_REVENUE_FEES)).isEqualByComparingTo("5.09");
        assertThat(merchantBalance(vendor)).isEqualByComparingTo("15.30"); // goods VAT stays with the merchant

        postings.recordOrderDelivered(tenant, older);
        // app.settlement.platform-vat-rate = 0: the older order adds no VAT line, its fees are all revenue.
        assertThat(balance(LedgerAccountType.PLATFORM_VAT_PAYABLE)).isEqualByComparingTo("0.81");
        assertThat(balance(LedgerAccountType.PLATFORM_REVENUE_FEES)).isEqualByComparingTo("15.09");
        assertThat(merchantBalance(vendor)).isEqualByComparingTo("116.30");
        assertTenantBalanced();
    }

    @Test
    void aVendorOverrideRateWinsOverTheDefaultPlan() {
        jdbc.update("INSERT INTO commission_plans (id, tenant_id, name, rate, is_default, effective_from) VALUES (?, ?, 'Padrão', 0.15, TRUE, '2026-01-01')",
                UUID.randomUUID(), tenant);
        jdbc.update("INSERT INTO vendor_commission_overrides (id, tenant_id, vendor_id, rate, effective_from) VALUES (?, ?, ?, 0.10, '2026-01-01')",
                UUID.randomUUID(), tenant, vendor);
        UUID other = TestParents.vendor(jdbc, tenant);
        UUID order = order("DELIVERED", "126.00", "100.00", "10.00", "16.00", "0.00", true);
        UUID otherOrder = orderFor(other, "DELIVERED", "126.00", "100.00", "10.00", "16.00", "0.00", true);

        postings.recordOrderDelivered(tenant, order);
        postings.recordOrderDelivered(tenant, otherOrder);

        assertThat(jdbc.queryForObject("SELECT commission_amount FROM commissions WHERE order_id = ?", BigDecimal.class, order))
                .isEqualByComparingTo("10.00");
        assertThat(merchantBalance(vendor)).isEqualByComparingTo("106.00");
        assertThat(jdbc.queryForObject("SELECT commission_amount FROM commissions WHERE order_id = ?", BigDecimal.class, otherOrder))
                .isEqualByComparingTo("15.00");
        assertThat(jdbc.queryForObject("SELECT plan_id IS NOT NULL FROM commissions WHERE order_id = ?", Boolean.class, otherOrder)).isTrue();
    }

    @Test
    void balanceEndpointConfinesAVendorAdminToItsOwnVendor() throws Exception {
        UUID order = order("DELIVERED", "126.00", "100.00", "10.00", "16.00", "0.00", true);
        postings.recordOrderDelivered(tenant, order);
        UUID other = TestParents.vendor(jdbc, tenant);
        String merchant = merchantToken(vendor);

        mockMvc.perform(get("/api/v1/finance/merchants/{id}/balance", vendor).header("Authorization", "Bearer " + merchant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(101.00));
        mockMvc.perform(get("/api/v1/finance/merchants/{id}/balance", other).header("Authorization", "Bearer " + merchant))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("vendor_access_denied"));
        mockMvc.perform(get("/api/v1/finance/ledger/transactions").header("Authorization", "Bearer " + merchant))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/finance/merchants/{id}/balance", UUID.randomUUID()).header("Authorization", "Bearer " + token("FINANCE")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/finance/merchants/{id}/balance", other).header("Authorization", "Bearer " + token("FINANCE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0));
        mockMvc.perform(get("/api/v1/finance/merchants/{id}/balance", vendor).header("Authorization", "Bearer " + token("OPS")))
                .andExpect(status().isForbidden());
    }

    @Test
    void reconciliationFindsTheLedgerBalanced() {
        UUID order = order("DELIVERED", "126.00", "100.00", "10.00", "16.00", "0.00", true);
        postings.recordOrderDelivered(tenant, order);

        LedgerReconciliation.Result result = reconciliation.reconcile();

        assertThat(result.balanced()).isTrue();
        assertThat(result.imbalancedTenants()).doesNotContain(tenant);
    }

    // --- helpers ---

    private UUID header(String key) {
        return ledger.insertTransaction(tenant, key, LedgerTransactionType.PAYMENT_CAPTURED, "TEST", UUID.randomUUID(), null, null,
                Instant.now(), "test").orElseThrow();
    }

    private UUID order(String status, String total, String subtotal, String fees, String taxes, String discount, boolean captured) {
        return orderFor(vendor, status, total, subtotal, fees, taxes, discount, captured);
    }

    /** Seeds an order (+ DELIVERED history row when delivered) and its payment, CONFIRMED when {@code captured}. */
    private UUID orderFor(UUID vendorId, String status, String total, String subtotal, String fees, String taxes, String discount,
                          boolean captured) {
        UUID id = UUID.randomUUID();
        Instant created = Instant.now().minusSeconds(3600);
        Timestamp createdTs = Timestamp.from(created);
        jdbc.update("""
                INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, status, subtotal, fees, taxes, discounts,
                                    discount_total, total, checkout_idempotency_key, delivery_confirmation_code_hash,
                                    created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, 'hash', ?, ?)""",
                id, tenant, "ref-" + id, customer, vendorId, status, new BigDecimal(subtotal), new BigDecimal(fees), new BigDecimal(taxes),
                new BigDecimal(discount), new BigDecimal(total), "key-" + id, createdTs, createdTs);
        if ("DELIVERED".equals(status)) {
            history(id, "DELIVERING", "DELIVERED", created.plusSeconds(1800));
        }
        jdbc.update("""
                INSERT INTO payments (id, tenant_id, order_id, amount, provider, idempotency_key, status, confirmed_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'LOCAL_MOCK', ?, ?, ?, ?, ?)""",
                UUID.randomUUID(), tenant, id, new BigDecimal(total), "key-" + id, captured ? "CONFIRMED" : "PENDING_CONFIRMATION",
                captured ? Timestamp.from(created.plusSeconds(60)) : null, createdTs, createdTs);
        return id;
    }

    private void history(UUID order, String from, String to, Instant at) {
        jdbc.update("INSERT INTO order_status_history (id, tenant_id, order_id, from_status, to_status, actor_type, reason, created_at) VALUES (?, ?, ?, ?, ?, 'SYSTEM', 'TEST', ?)",
                UUID.randomUUID(), tenant, order, from, to, Timestamp.from(at));
    }

    private UUID refund(UUID payment, UUID order, String amount, String status, Instant at) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO refunds (id, tenant_id, payment_id, order_id, amount, reason, status, idempotency_key, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'test', ?, ?, ?, ?)",
                id, tenant, payment, order, new BigDecimal(amount), status, "refund-" + id, Timestamp.from(at), Timestamp.from(at));
        return id;
    }

    private UUID paymentOf(UUID order) {
        return jdbc.queryForObject("SELECT id FROM payments WHERE order_id = ?", UUID.class, order);
    }

    private long transactions(LedgerTransactionType type) {
        return count("SELECT COUNT(*) FROM ledger_transactions WHERE tenant_id = ? AND transaction_type = ?", tenant, type.name());
    }

    private BigDecimal merchantBalance(UUID vendorId) {
        LedgerRepository.AccountTotals totals = ledger.totals(tenant, LedgerAccountType.MERCHANT_PAYABLE, vendorId);
        return totals.credit().subtract(totals.debit());
    }

    /** Balance in the account's normal direction. */
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

    private <T> List<T> concurrently(int threads, Callable<T> body) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> { start.await(); return body.call(); }));
        }
        start.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(future.get(60, TimeUnit.SECONDS));
        }
        return results;
    }

    private String token(String role) {
        return jwt("kc-" + role.toLowerCase() + "-" + UUID.randomUUID(), role);
    }

    private String merchantToken(UUID vendorId) {
        String subject = "kc-merchant-" + UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'Merchant', 'ACTIVE', ?, ?)",
                profileId, tenant, subject, subject + "@example.test", now, now);
        jdbc.update("INSERT INTO app_user_profile_roles (app_user_profile_id, role) VALUES (?, 'VENDOR_ADMIN')", profileId);
        jdbc.update("INSERT INTO merchant_users (id, tenant_id, vendor_id, app_user_profile_id, role) VALUES (?, ?, ?, ?, 'OWNER')",
                UUID.randomUUID(), tenant, vendorId, profileId);
        return jwt(subject, "VENDOR_ADMIN");
    }

    private String jwt(String subject, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        claims.put("azp", "delivery-app");
        claims.put("tenant_id", tenant.toString());
        claims.put("realm_access", Map.of("roles", List.of(role)));
        String value = "ledger-token-" + UUID.randomUUID();
        tokens.put(value, new Jwt(value, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "RS256"), claims));
        return value;
    }
}
