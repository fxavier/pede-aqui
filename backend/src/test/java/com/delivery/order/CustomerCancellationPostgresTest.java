package com.delivery.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.PostgresApiE2ETestBase;
import com.delivery.common.TestParents;
import com.delivery.common.security.TenancyProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

/**
 * LAC-26 (W5-A) end to end on real Postgres: POST /me/orders/{id}/cancel in each policy phase (free cancel with and
 * without a captured payment, request after acceptance, 409 from READY_FOR_PICKUP on), the OPS approve/reject queue
 * with the fee arithmetic, idempotent repeats, automatic closure of a stale request, and the races with the vendor
 * (accept vs. cancel, ready-for-pickup vs. approval) that must end with exactly one outcome and no doubled effect.
 */
class CustomerCancellationPostgresTest extends PostgresApiE2ETestBase {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;
    @Autowired private TenancyProperties tenancyProperties;
    @Autowired private ObjectMapper objectMapper;

    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
    private UUID tenantId;
    private UUID vendorId;
    private UUID categoryId;
    private String merchant;

    @BeforeEach
    void seed() {
        tenantId = tenancyProperties.getOperatorTenantId();
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            Jwt jwt = tokens.get(invocation.<String>getArgument(0));
            if (jwt == null) throw new BadJwtException("unknown test token");
            return jwt;
        });
        Timestamp now = Timestamp.from(Instant.now());
        vendorId = TestParents.vendor(jdbc, tenantId);
        categoryId = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, tenant_id, name, slug, active, created_at, updated_at) VALUES (?, ?, 'C', ?, TRUE, ?, ?)",
                categoryId, tenantId, "slug-" + categoryId, now, now);
        merchant = merchantOf(vendorId);
    }

    // --- free phase ------------------------------------------------------------------------------

    @Test
    void pendingPaymentIsCancelledAtOnceWithoutRefundAndRepeatsAreNoOps() throws Exception {
        Seeded seeded = seedOrder("PAYMENT_PENDING", "PENDING_CONFIRMATION");

        cancel(seeded, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("CANCELLED"))
                .andExpect(jsonPath("$.orderStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("CUSTOMER_CANCELLED"))
                .andExpect(jsonPath("$.refundAmount").doesNotExist());

        assertThat(order(seeded)).containsEntry("status", "CANCELLED").containsEntry("cancellation_reason", "CUSTOMER_CANCELLED")
                .containsEntry("stock_status", "RELEASED");
        assertThat(order(seeded).get("delivery_confirmation_code_display")).isNull();
        assertThat(paymentStatus(seeded)).isEqualTo("CANCELLED");
        assertThat(deliveryStatus(seeded)).isEqualTo("CANCELLED");
        assertThat(inventory(seeded)).containsEntry("quantity_available", 100).containsEntry("quantity_reserved", 0);
        assertThat(refunds(seeded)).isEmpty();
        assertThat(orderHistory(seeded)).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("from_status", "PAYMENT_PENDING").containsEntry("to_status", "CANCELLED")
                .containsEntry("actor_type", "USER").containsEntry("reason", "CUSTOMER_CANCELLED"));
        assertThat(jdbc.queryForList("SELECT from_status, to_status, reason FROM payment_status_history WHERE order_id = ?", seeded.orderId()))
                .singleElement().satisfies(row -> assertThat(row).containsEntry("to_status", "CANCELLED").containsEntry("reason", "CUSTOMER_CANCELLED"));
        assertThat(audits(seeded.orderId(), "ORDER_CANCELLED_BY_CUSTOMER")).isEqualTo(1);
        assertThat(notifications(seeded, "order_cancelled_by_customer")).isEqualTo(1);

        // Idempotent repeat: same answer, nothing new.
        cancel(seeded, null).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("CANCELLED"));
        assertThat(orderHistory(seeded)).hasSize(1);
        assertThat(audits(seeded.orderId(), "ORDER_CANCELLED_BY_CUSTOMER")).isEqualTo(1);
        assertThat(notifications(seeded, "order_cancelled_by_customer")).isEqualTo(1);
        assertThat(inventory(seeded)).containsEntry("quantity_available", 100).containsEntry("quantity_reserved", 0);
    }

    @Test
    void confirmedNotAcceptedOrderIsCancelledWithAFullRefundOnce() throws Exception {
        Seeded seeded = seedOrder("PAYMENT_CONFIRMED", "CONFIRMED");

        cancel(seeded, "{\"reason\":\"Encomendei por engano\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("CANCELLED"))
                .andExpect(jsonPath("$.refundAmount").value(100.00));

        assertThat(order(seeded)).containsEntry("status", "CANCELLED").containsEntry("cancellation_reason", "CUSTOMER_CANCELLED")
                .containsEntry("stock_status", "RELEASED");
        assertThat(refunds(seeded)).singleElement().satisfies(row -> {
            assertThat(row.get("amount").toString()).isEqualTo("100.00");
            assertThat(row).containsEntry("status", "REQUESTED").containsEntry("idempotency_key", "customer-cancel-" + seeded.orderId());
        });
        assertThat(paymentStatus(seeded)).isEqualTo("CONFIRMED"); // the financial state changes on refund approval
        assertThat(deliveryStatus(seeded)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT business_reference FROM audit_logs WHERE target_id = ? AND action = 'ORDER_CANCELLED_BY_CUSTOMER'",
                String.class, seeded.orderId().toString())).isEqualTo("refund_remaining=100.00");

        cancel(seeded, null).andExpect(status().isOk()).andExpect(jsonPath("$.refundAmount").value(100.00));
        assertThat(refunds(seeded)).hasSize(1);
        assertThat(orderHistory(seeded)).hasSize(1);
    }

    @Test
    void anotherCustomersOrderIsA404WithoutSideEffects() throws Exception {
        Seeded seeded = seedOrder("PAYMENT_CONFIRMED", "CONFIRMED");
        String stranger = customerToken(TestParents.customer(jdbc, tenantId));

        mockMvc.perform(post("/api/v1/me/orders/{id}/cancel", seeded.orderId()).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/me/orders/{id}/cancel", UUID.randomUUID()).header("Authorization", "Bearer " + customerToken(seeded.customerId())))
                .andExpect(status().isNotFound());
        assertThat(order(seeded)).containsEntry("status", "PAYMENT_CONFIRMED");
        assertThat(refunds(seeded)).isEmpty();
    }

    @Test
    void fromReadyForPickupOnTheOrderIsNotCancellable() throws Exception {
        for (String status : List.of("READY_FOR_PICKUP", "ASSIGNED_TO_COURIER", "DELIVERED", "REFUNDED")) {
            Seeded seeded = seedOrder(status, "CONFIRMED");
            cancel(seeded, null).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("order_not_cancellable"))
                    .andExpect(jsonPath("$.details.orderStatus").value(status));
            assertThat(order(seeded)).containsEntry("status", status);
            assertThat(refunds(seeded)).isEmpty();
            assertThat(orderHistory(seeded)).isEmpty();
        }
    }

    // --- request phase + OPS decisions ------------------------------------------------------------

    @Test
    void acceptedOrderGetsOneRequestThatOpsApprovesWithTheFeeWithheld() throws Exception {
        Seeded seeded = seedOrder("ACCEPTED_BY_VENDOR", "CONFIRMED");

        JsonNode first = json(cancel(seeded, "{\"reason\":\"Já não preciso\"}").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.outcome").value("REQUESTED"))
                .andExpect(jsonPath("$.orderStatus").value("ACCEPTED_BY_VENDOR"))
                .andExpect(jsonPath("$.request.status").value("REQUESTED"))
                .andExpect(jsonPath("$.request.feeAmount").value(9.00)) // 10 % of the 90.00 subtotal
                .andExpect(jsonPath("$.request.reason").value("Já não preciso")));
        String requestId = first.at("/request/id").asText();
        // Idempotent repeat: the same open request, no second row.
        cancel(seeded, null).andExpect(status().isAccepted()).andExpect(jsonPath("$.request.id").value(requestId));
        assertThat(requestRows(seeded)).hasSize(1);
        assertThat(order(seeded)).containsEntry("status", "ACCEPTED_BY_VENDOR").containsEntry("stock_status", "RESERVED");
        assertThat(refunds(seeded)).isEmpty();
        assertThat(audits(UUID.fromString(requestId), "ORDER_CANCELLATION_REQUESTED")).isEqualTo(1);

        // The customer sees the pending request on the order detail.
        mockMvc.perform(get("/api/v1/me/orders/{id}", seeded.orderId()).header("Authorization", "Bearer " + customerToken(seeded.customerId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellationPhase").value("REQUEST"))
                .andExpect(jsonPath("$.cancellationFeeEstimate").value(9.00))
                .andExpect(jsonPath("$.cancellationRequest.id").value(requestId))
                .andExpect(jsonPath("$.cancellationRequest.status").value("REQUESTED"));

        // OPS queue.
        String ops = staffToken("OPS");
        mockMvc.perform(get("/api/v1/ops/cancellation-requests").param("size", "100").header("Authorization", "Bearer " + ops))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + requestId + "')].orderReference").value("ref-" + seeded.orderId()));
        mockMvc.perform(get("/api/v1/ops/cancellation-requests").header("Authorization", "Bearer " + customerToken(seeded.customerId())))
                .andExpect(status().isForbidden());

        approve(requestId, ops).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.feeAmount").value(9.00))
                .andExpect(jsonPath("$.refundAmount").value(91.00))
                .andExpect(jsonPath("$.orderStatus").value("CANCELLED"));

        assertThat(order(seeded)).containsEntry("status", "CANCELLED").containsEntry("cancellation_reason", "CUSTOMER_CANCELLED_WITH_FEE")
                .containsEntry("stock_status", "RELEASED");
        assertThat(deliveryStatus(seeded)).isEqualTo("CANCELLED");
        assertThat(inventory(seeded)).containsEntry("quantity_available", 100).containsEntry("quantity_reserved", 0);
        assertThat(refunds(seeded)).singleElement().satisfies(row -> {
            assertThat(row.get("amount").toString()).isEqualTo("91.00");
            assertThat(row).containsEntry("idempotency_key", "customer-cancel-" + seeded.orderId());
        });
        assertThat(orderHistory(seeded)).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("from_status", "ACCEPTED_BY_VENDOR").containsEntry("reason", "CUSTOMER_CANCELLED_WITH_FEE"));
        assertThat(jdbc.queryForObject("SELECT business_reference FROM audit_logs WHERE target_id = ? AND action = 'ORDER_CANCELLATION_APPROVED'",
                String.class, seeded.orderId().toString())).isEqualTo("refund_remaining=91.00;fee=9.00");
        assertThat(notifications(seeded, "order_cancellation_approved")).isEqualTo(1);

        // Repeats: approval is idempotent, rejection of an approved request is a 409, the customer sees CANCELLED.
        approve(requestId, ops).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        reject(requestId, ops).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("cancellation_request_closed"));
        cancel(seeded, null).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("CANCELLED"))
                .andExpect(jsonPath("$.refundAmount").value(91.00));
        assertThat(refunds(seeded)).hasSize(1);
        assertThat(orderHistory(seeded)).hasSize(1);
        assertThat(audits(seeded.orderId(), "ORDER_CANCELLATION_APPROVED")).isEqualTo(1);
    }

    @Test
    void feeIsWithheldFromWhatRemainsAfterAnEarlierPartialRefund() throws Exception {
        Seeded seeded = seedOrder("PREPARING", "PARTIALLY_REFUNDED");
        jdbc.update("INSERT INTO refunds (id, tenant_id, payment_id, order_id, amount, reason, status, idempotency_key, created_at, updated_at) VALUES (?, ?, ?, ?, 95.00, 'earlier', 'REFUNDED', ?, now(), now())",
                UUID.randomUUID(), tenantId, seeded.paymentId(), seeded.orderId(), "earlier-" + seeded.orderId());
        String requestId = json(cancel(seeded, null).andExpect(status().isAccepted())).at("/request/id").asText();

        // Refundable 5.00 < quoted fee 9.00: the fee is capped, nothing is refunded (no refund row), never negative.
        approve(requestId, staffToken("ADMIN")).andExpect(status().isOk())
                .andExpect(jsonPath("$.feeAmount").value(5.00))
                .andExpect(jsonPath("$.refundAmount").value(0.00));
        assertThat(refunds(seeded)).hasSize(1); // only the earlier one
        assertThat(order(seeded)).containsEntry("status", "CANCELLED").containsEntry("cancellation_reason", "CUSTOMER_CANCELLED_WITH_FEE");
    }

    @Test
    void rejectedRequestLeavesTheOrderRunningAndANewRequestIsPossible() throws Exception {
        Seeded seeded = seedOrder("PREPARING", "CONFIRMED");
        String requestId = json(cancel(seeded, null).andExpect(status().isAccepted())).at("/request/id").asText();
        String ops = staffToken("OPS");

        reject(requestId, ops).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.closureReason").value("OPS_REJECTED"));
        reject(requestId, ops).andExpect(status().isOk()); // idempotent
        approve(requestId, ops).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("cancellation_request_closed"));
        assertThat(order(seeded)).containsEntry("status", "PREPARING").containsEntry("stock_status", "RESERVED");
        assertThat(refunds(seeded)).isEmpty();
        assertThat(notifications(seeded, "order_cancellation_rejected")).isEqualTo(1);
        assertThat(audits(UUID.fromString(requestId), "ORDER_CANCELLATION_REJECTED")).isEqualTo(1);

        String second = json(cancel(seeded, null).andExpect(status().isAccepted())).at("/request/id").asText();
        assertThat(second).isNotEqualTo(requestId);
        assertThat(requestRows(seeded)).hasSize(2);

        // Another tenant's OPS cannot see or decide it.
        UUID otherTenant = otherTenant();
        String foreignOps = token("kc-ops-" + UUID.randomUUID(), "OPS", Map.of("tenant_id", otherTenant.toString()));
        approve(second, foreignOps).andExpect(status().isNotFound());
        reject(second, foreignOps).andExpect(status().isNotFound());
    }

    @Test
    void approvalAfterTheOrderBecameReadyIsA409AndClosesTheRequest() throws Exception {
        Seeded seeded = seedOrder("PREPARING", "CONFIRMED");
        String requestId = json(cancel(seeded, null).andExpect(status().isAccepted())).at("/request/id").asText();
        mockMvc.perform(patch("/api/v1/vendor/orders/{id}/ready-for-pickup", seeded.orderId()).header("Authorization", "Bearer " + merchantToken()))
                .andExpect(status().isOk());

        approve(requestId, staffToken("OPS")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("order_not_cancellable"));

        assertThat(requestRows(seeded)).singleElement().satisfies(row ->
                assertThat(row).containsEntry("status", "REJECTED").containsEntry("closure_reason", "ORDER_ADVANCED"));
        assertThat(order(seeded)).containsEntry("status", "READY_FOR_PICKUP").containsEntry("stock_status", "RESERVED");
        assertThat(refunds(seeded)).isEmpty();
        assertThat(audits(UUID.fromString(requestId), "ORDER_CANCELLATION_REQUEST_CLOSED")).isEqualTo(1);
        // The customer is refused too.
        cancel(seeded, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("order_not_cancellable"));
    }

    // --- races --------------------------------------------------------------------------------------

    @Test
    void vendorAcceptRacingTheCustomerCancelHasExactlyOneOutcome() throws Exception {
        for (int round = 0; round < 6; round++) {
            Seeded seeded = seedOrder("PAYMENT_CONFIRMED", "CONFIRMED");
            String merchantToken = merchantToken();
            int[] codes = new int[2];

            race(() -> codes[0] = mockMvc.perform(patch("/api/v1/vendor/orders/{id}/accept", seeded.orderId())
                            .header("Authorization", "Bearer " + merchantToken)).andReturn().getResponse().getStatus(),
                    () -> codes[1] = cancel(seeded, null).andReturn().getResponse().getStatus());

            String status = (String) order(seeded).get("status");
            assertThat(status).as("round " + round).isIn("ACCEPTED_BY_VENDOR", "CANCELLED");
            assertThat(orderHistory(seeded)).as("round " + round).singleElement()
                    .satisfies(row -> assertThat(row).containsEntry("from_status", "PAYMENT_CONFIRMED").containsEntry("to_status", status));
            if (status.equals("CANCELLED")) {
                assertThat(codes[1]).isEqualTo(200);
                assertThat(codes[0]).isEqualTo(409); // invalid_order_transition or concurrent_modification
                assertThat(refunds(seeded)).hasSize(1);
                assertThat(inventory(seeded)).containsEntry("quantity_available", 100).containsEntry("quantity_reserved", 0);
                assertThat(requestRows(seeded)).isEmpty();
            } else {
                assertThat(codes[0]).isEqualTo(200);
                // The customer either lost on the order version (409, nothing written) or came second (202 request).
                assertThat(codes[1]).isIn(202, 409);
                assertThat(refunds(seeded)).isEmpty();
                assertThat(inventory(seeded)).containsEntry("quantity_available", 95).containsEntry("quantity_reserved", 5);
                assertThat(requestRows(seeded)).hasSize(codes[1] == 202 ? 1 : 0);
            }
        }
    }

    @Test
    void concurrentCustomerCallsCreateOneCancellationAndOneRequest() throws Exception {
        Seeded free = seedOrder("PAYMENT_CONFIRMED", "CONFIRMED");
        int[] codes = new int[2];
        race(() -> codes[0] = cancel(free, null).andReturn().getResponse().getStatus(),
                () -> codes[1] = cancel(free, null).andReturn().getResponse().getStatus());
        assertThat(codes).containsOnly(200);
        assertThat(refunds(free)).hasSize(1);
        assertThat(orderHistory(free)).hasSize(1);
        assertThat(audits(free.orderId(), "ORDER_CANCELLED_BY_CUSTOMER")).isEqualTo(1);

        Seeded request = seedOrder("ACCEPTED_BY_VENDOR", "CONFIRMED");
        race(() -> codes[0] = cancel(request, null).andReturn().getResponse().getStatus(),
                () -> codes[1] = cancel(request, null).andReturn().getResponse().getStatus());
        assertThat(codes).containsOnly(202);
        assertThat(requestRows(request)).hasSize(1);
    }

    @Test
    void approvalRacingReadyForPickupHasExactlyOneOutcome() throws Exception {
        for (int round = 0; round < 4; round++) {
            Seeded seeded = seedOrder("PREPARING", "CONFIRMED");
            String requestId = json(cancel(seeded, null).andExpect(status().isAccepted())).at("/request/id").asText();
            String ops = staffToken("OPS");
            String merchantToken = merchantToken();
            int[] codes = new int[2];

            race(() -> codes[0] = approve(requestId, ops).andReturn().getResponse().getStatus(),
                    () -> codes[1] = mockMvc.perform(patch("/api/v1/vendor/orders/{id}/ready-for-pickup", seeded.orderId())
                            .header("Authorization", "Bearer " + merchantToken)).andReturn().getResponse().getStatus());

            String status = (String) order(seeded).get("status");
            assertThat(status).as("round " + round).isIn("CANCELLED", "READY_FOR_PICKUP");
            assertThat(orderHistory(seeded)).as("round " + round).singleElement()
                    .satisfies(row -> assertThat(row).containsEntry("to_status", status));
            if (status.equals("CANCELLED")) {
                assertThat(codes[0]).isEqualTo(200);
                assertThat(codes[1]).isEqualTo(409);
                assertThat(refunds(seeded)).hasSize(1);
                assertThat(requestRows(seeded)).singleElement().satisfies(row -> assertThat(row).containsEntry("status", "APPROVED"));
            } else {
                assertThat(codes[1]).isEqualTo(200);
                assertThat(codes[0]).isEqualTo(409);
                assertThat(refunds(seeded)).isEmpty();
                assertThat(order(seeded)).containsEntry("stock_status", "RESERVED");
                // Approval either came after the vendor (request closed) or lost on the order version (request still open).
                assertThat((String) requestRows(seeded).get(0).get("status")).isIn("REJECTED", "REQUESTED");
            }
        }
    }

    // --- helpers -----------------------------------------------------------------------------------

    private record Seeded(UUID orderId, UUID inventoryId, UUID paymentId, UUID customerId) {
    }

    @FunctionalInterface
    private interface Action {
        Object call() throws Exception;
    }

    private void race(Action first, Action second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Action action : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return action.call();
                }));
            }
            for (Future<Object> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private org.springframework.test.web.servlet.ResultActions cancel(Seeded seeded, String body) throws Exception {
        var request = post("/api/v1/me/orders/{id}/cancel", seeded.orderId())
                .header("Authorization", "Bearer " + customerToken(seeded.customerId()));
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private org.springframework.test.web.servlet.ResultActions approve(String requestId, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/ops/cancellation-requests/{id}/approve", requestId).header("Authorization", "Bearer " + token));
    }

    private org.springframework.test.web.servlet.ResultActions reject(String requestId, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/ops/cancellation-requests/{id}/reject", requestId).header("Authorization", "Bearer " + token));
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        MockHttpServletResponse response = result.andReturn().getResponse();
        return objectMapper.readTree(response.getContentAsString());
    }

    /** One order (subtotal 90, total 100) of 5 units reserved at checkout, its payment and a not-yet-dispatched delivery. */
    private Seeded seedOrder(String orderStatus, String paymentStatus) {
        Timestamp now = Timestamp.from(Instant.now());
        UUID productId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID inventoryId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID customerId = TestParents.customer(jdbc, tenantId);
        jdbc.update("""
                INSERT INTO products (id, tenant_id, vendor_id, category_id, name, status,
                                      requires_prescription_metadata, manual_validation_required, prohibited_fuel, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Arroz', 'ACTIVE', FALSE, FALSE, FALSE, ?, ?)""",
                productId, tenantId, vendorId, categoryId, now, now);
        jdbc.update("INSERT INTO skus (id, tenant_id, product_id, sku_code, name, price, active, created_at, updated_at) VALUES (?, ?, ?, ?, 'Arroz 1kg', 18.00, TRUE, ?, ?)",
                skuId, tenantId, productId, "sku-" + skuId, now, now);
        jdbc.update("INSERT INTO inventory_items (id, tenant_id, vendor_id, sku_id, quantity_available, quantity_reserved, status, created_at, updated_at, version) VALUES (?, ?, ?, ?, 95, 5, 'ACTIVE', ?, ?, 0)",
                inventoryId, tenantId, vendorId, skuId, now, now);
        jdbc.update("""
                INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, status, subtotal, fees, taxes, discounts,
                                    discount_total, total, checkout_idempotency_key, delivery_confirmation_code_hash,
                                    delivery_confirmation_code_display, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 90, 10, 0, 0, 0, 100, ?, 'hash', '000000', ?, ?)""",
                orderId, tenantId, "ref-" + orderId, customerId, vendorId, orderStatus, "key-" + orderId, now, now);
        jdbc.update("INSERT INTO order_items (id, order_id, tenant_id, sku_id, product_name_snapshot, sku_name_snapshot, unit_price_snapshot, quantity, line_total, created_at) VALUES (?, ?, ?, ?, 'Arroz', 'Arroz 1kg', 18.00, 5, 90.00, ?)",
                UUID.randomUUID(), orderId, tenantId, skuId, now);
        boolean pending = paymentStatus.equals("PENDING_CONFIRMATION");
        jdbc.update("INSERT INTO payments (id, tenant_id, order_id, amount, provider, idempotency_key, status, confirmed_at, created_at, updated_at) VALUES (?, ?, ?, 100.00, 'LOCAL_MOCK', ?, ?, ?, ?, ?)",
                paymentId, tenantId, orderId, "pay-" + paymentId, paymentStatus, pending ? null : now, now, now);
        jdbc.update("INSERT INTO deliveries (id, tenant_id, order_id, courier_id, status, confirmation_code_hash, confirmation_attempts, created_at, updated_at) VALUES (?, ?, ?, NULL, 'DISPATCH_PENDING', 'hash', 0, ?, ?)",
                UUID.randomUUID(), tenantId, orderId, now, now);
        return new Seeded(orderId, inventoryId, paymentId, customerId);
    }

    private Map<String, Object> order(Seeded seeded) {
        return jdbc.queryForMap("SELECT status, cancellation_reason, stock_status, delivery_confirmation_code_display FROM orders WHERE id = ?", seeded.orderId());
    }

    private String paymentStatus(Seeded seeded) {
        return jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, seeded.paymentId());
    }

    private String deliveryStatus(Seeded seeded) {
        return jdbc.queryForObject("SELECT status FROM deliveries WHERE order_id = ?", String.class, seeded.orderId());
    }

    private Map<String, Object> inventory(Seeded seeded) {
        return jdbc.queryForMap("SELECT quantity_available, quantity_reserved FROM inventory_items WHERE id = ?", seeded.inventoryId());
    }

    private List<Map<String, Object>> refunds(Seeded seeded) {
        return jdbc.queryForList("SELECT amount, status, idempotency_key FROM refunds WHERE order_id = ?", seeded.orderId());
    }

    private List<Map<String, Object>> requestRows(Seeded seeded) {
        return jdbc.queryForList("SELECT id, status, closure_reason, fee_amount, refund_amount FROM cancellation_requests WHERE order_id = ? ORDER BY requested_at",
                seeded.orderId());
    }

    private List<Map<String, Object>> orderHistory(Seeded seeded) {
        return jdbc.queryForList("SELECT from_status, to_status, actor_type, reason FROM order_status_history WHERE order_id = ? ORDER BY created_at, id",
                seeded.orderId());
    }

    private int audits(UUID targetId, String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE target_id = ? AND action = ?", Integer.class, targetId.toString(), action);
    }

    private int notifications(Seeded seeded, String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE business_reference = ? AND type = ?", Integer.class,
                "ref-" + seeded.orderId(), type);
    }

    private UUID otherTenant() {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, 'Other', ?, 'ACTIVE', 'MZN', ?, ?)",
                id, "other-" + id, now, now);
        return id;
    }

    private String merchantOf(UUID vendor) {
        Timestamp now = Timestamp.from(Instant.now());
        String subject = "kc-" + UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'Merchant', 'ACTIVE', ?, ?)",
                profileId, tenantId, subject, subject + "@example.test", now, now);
        jdbc.update("INSERT INTO app_user_profile_roles (app_user_profile_id, role) VALUES (?, 'VENDOR_ADMIN')", profileId);
        jdbc.update("INSERT INTO merchant_users (id, tenant_id, vendor_id, app_user_profile_id, role) VALUES (?, ?, ?, ?, 'OWNER')",
                UUID.randomUUID(), tenantId, vendor, profileId);
        return subject;
    }

    private String merchantToken() {
        return token(merchant, "VENDOR_ADMIN", Map.of("tenant_id", tenantId.toString()));
    }

    private String staffToken(String role) {
        return token("kc-staff-" + UUID.randomUUID(), role, Map.of("tenant_id", tenantId.toString()));
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
        String value = "cancel-token-" + UUID.randomUUID();
        tokens.put(value, new Jwt(value, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "RS256"), claims));
        return value;
    }
}
