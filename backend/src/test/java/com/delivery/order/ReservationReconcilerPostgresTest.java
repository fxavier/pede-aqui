package com.delivery.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.common.PostgresApiE2ETestBase;
import com.delivery.common.TestParents;
import com.delivery.order.job.ReservationReconcilerJob;
import com.delivery.order.service.ReservationReconciler;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * LAC-23 on real Postgres (shared full context): the reconciler releases terminal-before-pickup reservations, commits
 * delivered ones, leaves live / recently-touched / ops-review orders alone, is idempotent, and skips an order whose
 * payment row is locked by a request in flight (lock order rule, SKIP LOCKED) until a later run.
 */
class ReservationReconcilerPostgresTest extends PostgresApiE2ETestBase {

    private static final Instant OLD = Instant.now().minus(Duration.ofHours(1));

    @Autowired private ReservationReconciler reconciler;
    @Autowired private ReservationReconcilerJob job;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MeterRegistry meterRegistry;
    @Autowired private PlatformTransactionManager transactionManager;

    private UUID tenantId;
    private UUID vendorId;
    private UUID categoryId;

    @BeforeEach
    void seedTenant() {
        tenantId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', 'MZN', ?, ?)",
                tenantId, "Reconciler Tenant", "reconciler-" + tenantId, now, now);
        vendorId = TestParents.vendor(jdbc, tenantId);
        categoryId = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, tenant_id, name, slug, active, created_at, updated_at) VALUES (?, ?, 'C', ?, TRUE, ?, ?)",
                categoryId, tenantId, "slug-" + categoryId, now, now);
    }

    @Test
    void releasesCommitsAndLeavesOtherOrdersAloneIdempotently() {
        Seeded cancelled = seedOrder("CANCELLED", null, OLD);
        Seeded refundedNoCourier = seedOrder("REFUNDED", "CANCELLED", OLD);
        Seeded delivered = seedOrder("DELIVERED", "DELIVERED", OLD);
        Seeded pickedUpByDelivery = seedOrder("ASSIGNED_TO_COURIER", "PICKED_UP", OLD);
        Seeded live = seedOrder("PAYMENT_CONFIRMED", null, OLD);
        Seeded recentlyCancelled = seedOrder("CANCELLED", null, Instant.now());
        Seeded refundedCourierEngaged = seedOrder("REFUNDED", "ACCEPTED", OLD);
        double releasedBefore = counter("released");

        ReservationReconciler.Result first = reconciler.reconcile();

        assertThat(first.released()).isGreaterThanOrEqualTo(2);
        assertThat(first.committed()).isGreaterThanOrEqualTo(2);
        assertThat(first.needsAttention()).isGreaterThanOrEqualTo(1);
        assertThat(counter("released") - releasedBefore).isGreaterThanOrEqualTo(2);
        // release: reserved units go back to available (95/5 -> 100/0)
        assertReleased(cancelled);
        assertReleased(refundedNoCourier);
        // commit: reserved units are consumed (95/5 -> 95/0)
        assertCommitted(delivered);
        assertCommitted(pickedUpByDelivery);
        // untouched
        assertUntouched(live);
        assertUntouched(recentlyCancelled);
        assertUntouched(refundedCourierEngaged);

        // Idempotent: a second run changes nothing for these orders.
        reconciler.reconcile();
        job.run(); // the scheduled entry point goes through JobRunner (metrics + logging) and the same logic
        assertReleased(cancelled);
        assertCommitted(delivered);
        assertUntouched(live);
        assertThat(meterRegistry.find("job.runs").tags("job", ReservationReconcilerJob.NAME, "result", "success").counter())
                .isNotNull();
    }

    @Test
    void skipsAnOrderWhosePaymentIsLockedByARequestInFlightAndFixesItNextRun() throws Exception {
        Seeded cancelled = seedOrder("CANCELLED", null, OLD);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.queryForList("SELECT id FROM payments WHERE id = ? FOR UPDATE", cancelled.paymentId());
            locked.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        holder.start();
        try {
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            ReservationReconciler.Result busy = reconciler.reconcile();

            assertThat(busy.skippedBusy()).isGreaterThanOrEqualTo(1);
            assertUntouched(cancelled);
        } finally {
            release.countDown();
            holder.join(10_000);
        }

        reconciler.reconcile();
        assertReleased(cancelled);
    }

    // --- helpers ---

    private record Seeded(UUID orderId, UUID inventoryId, UUID paymentId) {
    }

    /** One order of 5 units reserved at checkout (inventory 95 available + 5 reserved), with payment and optional delivery. */
    private Seeded seedOrder(String orderStatus, String deliveryStatus, Instant updatedAt) {
        Timestamp now = Timestamp.from(Instant.now());
        Timestamp touched = Timestamp.from(updatedAt);
        UUID productId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID inventoryId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
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
                VALUES (?, ?, ?, ?, ?, ?, 90, 10, 0, 0, 0, 100, ?, 'hash', NULL, ?, ?)""",
                orderId, tenantId, "ref-" + orderId, TestParents.customer(jdbc, tenantId), vendorId, orderStatus, "key-" + orderId, touched, touched);
        jdbc.update("INSERT INTO order_items (id, order_id, tenant_id, sku_id, product_name_snapshot, sku_name_snapshot, unit_price_snapshot, quantity, line_total, created_at) VALUES (?, ?, ?, ?, 'Arroz', 'Arroz 1kg', 18.00, 5, 90.00, ?)",
                UUID.randomUUID(), orderId, tenantId, skuId, now);
        jdbc.update("INSERT INTO payments (id, tenant_id, order_id, amount, provider, idempotency_key, status, created_at, updated_at) VALUES (?, ?, ?, 100.00, 'LOCAL_MOCK', ?, 'CONFIRMED', ?, ?)",
                paymentId, tenantId, orderId, "pay-" + paymentId, now, now);
        if (deliveryStatus != null) {
            jdbc.update("INSERT INTO deliveries (id, tenant_id, order_id, courier_id, status, confirmation_code_hash, confirmation_attempts, created_at, updated_at) VALUES (?, ?, ?, NULL, ?, 'hash', 0, ?, ?)",
                    UUID.randomUUID(), tenantId, orderId, deliveryStatus, now, now);
        }
        return new Seeded(orderId, inventoryId, paymentId);
    }

    private void assertReleased(Seeded seeded) {
        assertThat(stockStatus(seeded)).isEqualTo("RELEASED");
        assertThat(inventory(seeded)).containsEntry("quantity_available", 100).containsEntry("quantity_reserved", 0);
    }

    private void assertCommitted(Seeded seeded) {
        assertThat(stockStatus(seeded)).isEqualTo("COMMITTED");
        assertThat(inventory(seeded)).containsEntry("quantity_available", 95).containsEntry("quantity_reserved", 0);
    }

    private void assertUntouched(Seeded seeded) {
        assertThat(stockStatus(seeded)).isEqualTo("RESERVED");
        assertThat(inventory(seeded)).containsEntry("quantity_available", 95).containsEntry("quantity_reserved", 5);
    }

    private String stockStatus(Seeded seeded) {
        return jdbc.queryForObject("SELECT stock_status FROM orders WHERE id = ?", String.class, seeded.orderId());
    }

    private Map<String, Object> inventory(Seeded seeded) {
        return jdbc.queryForMap("SELECT quantity_available, quantity_reserved FROM inventory_items WHERE id = ?", seeded.inventoryId());
    }

    private double counter(String outcome) {
        var counter = meterRegistry.find("stock.reconciler.orders").tags("outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }
}
