package com.delivery.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.delivery.auth.api.UserAccounts;
import com.delivery.common.PostgresApiE2ETestBase;
import com.delivery.common.TestParents;
import com.delivery.notification.api.NotificationChannel;
import com.delivery.notification.api.NotificationDispatcher;
import com.delivery.notification.api.NotificationRequest;
import com.delivery.notification.config.NotificationProperties;
import com.delivery.notification.repository.DeviceTokenRepository;
import com.delivery.notification.repository.NotificationDeliveryStore;
import com.delivery.notification.repository.UserNotificationPreferenceRepository;
import com.delivery.notification.sender.EmailSender;
import com.delivery.notification.sender.SmsSender;
import com.delivery.notification.repository.NotificationDeliveryStore.ClaimedDelivery;
import com.delivery.notification.sender.PushSender;
import com.delivery.notification.sender.SendResult;
import com.delivery.notification.service.NotificationDeliveryWorker;
import com.delivery.notification.service.NotificationService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * LAC-48 outbox on real Postgres: idempotent dispatch (UNIQUE idempotency key), the delivery job's retry with
 * exponential backoff and give-up, the in-app fan-out, and the claim's {@code FOR UPDATE SKIP LOCKED} + lease under
 * concurrency. Runs in the shared E2E context (no extra connection pool): the worker under test is built here from the
 * context's store/repositories with a mocked push provider and max-attempts 3.
 */
class NotificationOutboxPostgresTest extends PostgresApiE2ETestBase {

    private final PushSender pushSender = mock(PushSender.class);
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NotificationDispatcher dispatcher;
    @Autowired private NotificationService notificationService;
    @Autowired private DeviceTokenRepository deviceTokenRepository;
    @Autowired private UserNotificationPreferenceRepository preferenceRepository;
    @Autowired private UserAccounts userAccounts;
    private NotificationDeliveryWorker worker;
    @Autowired private NotificationDeliveryStore store;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private UUID tenant;
    private UUID customer;

    @BeforeEach
    void seed() {
        reset(pushSender);
        tx = new TransactionTemplate(transactionManager);
        NotificationProperties properties = new NotificationProperties();
        properties.setMaxAttempts(3);
        properties.setBaseBackoff(Duration.ofSeconds(30));
        worker = new NotificationDeliveryWorker(store, transactionManager, properties, pushSender, mock(SmsSender.class),
                mock(EmailSender.class), deviceTokenRepository, preferenceRepository, userAccounts);
        tenant = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', 'MZN', now(), now())",
                tenant, "Tenant " + tenant, "slug-" + tenant);
        customer = TestParents.customer(jdbc, tenant);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO device_tokens (id, tenant_id, user_profile_id, platform, token, token_hash, created_at, last_seen_at) VALUES (?, ?, ?, 'ANDROID', ?, ?, ?, ?)",
                UUID.randomUUID(), tenant, customer, token(), "hash-" + customer, now, now);
        parkForeignDueDeliveries();
    }

    /**
     * Isolation from the shared database: other test classes (order flows, sales, timeouts…) leave QUEUED deliveries
     * behind, and the real claim is global ({@code ORDER BY next_attempt_at} across tenants) — so a batch could be
     * filled with their older rows and miss this test's ones (order-dependent failures). Their rows are pushed a day
     * into the future (still QUEUED, nothing deleted) so that every claim here only sees this test's tenant. Called
     * before each claim too, because async after-commit listeners of earlier tests may still be writing rows.
     */
    private void parkForeignDueDeliveries() {
        jdbc.update("UPDATE notification_deliveries SET next_attempt_at = now() + interval '1 day' "
                + "WHERE status = 'QUEUED' AND next_attempt_at <= now() + interval '1 hour' AND tenant_id <> ?", tenant);
    }

    @AfterEach
    void cleanUp() {
        // Deleting the tenant cascades device_tokens / notification_deliveries (V067); notifications reference the profile.
        jdbc.update("DELETE FROM notifications WHERE tenant_id = ?", tenant);
        jdbc.update("DELETE FROM app_user_profiles WHERE tenant_id = ?", tenant);
        jdbc.update("DELETE FROM tenants WHERE id = ?", tenant);
    }

    @Test
    void aRepeatedDispatchOfTheSameFactWritesOneRowPerChannel() {
        assertThat(dispatch("order_rejected", "PA-1", "fact-" + tenant)).isEqualTo(2); // PUSH queued + SMS skipped (opt-in off)
        assertThat(dispatch("order_rejected", "PA-1", "fact-" + tenant)).isZero();

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT channel, status, last_error, body FROM notification_deliveries WHERE tenant_id = ? ORDER BY channel", tenant);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("channel", "PUSH").containsEntry("status", "QUEUED");
        assertThat((String) rows.get(0).get("body")).contains("PA-1");
        assertThat(rows.get(1)).containsEntry("channel", "SMS").containsEntry("status", "SKIPPED").containsEntry("last_error", "preference_disabled");
    }

    @Test
    void inAppNotificationsWithAnExternalTemplateFanOutInTheSameTransaction() {
        tx.executeWithoutResult(status -> notificationService.createForTenant(tenant, customer, "CUSTOMER", "order_cancelled",
                "Order Cancelled", "Your order PA-9 was cancelled. Reason: private", "PA-9"));
        tx.executeWithoutResult(status -> notificationService.createForTenant(tenant, customer, "CUSTOMER", "order_status",
                "Order Status", "no template for this type", "PA-9"));

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT template_key, channel, body FROM notification_deliveries WHERE tenant_id = ?", tenant);
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("template_key", "order_cancelled").containsEntry("channel", "PUSH");
            assertThat((String) row.get("body")).isEqualTo("O seu pedido PA-9 foi cancelado.");
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE tenant_id = ?", Integer.class, tenant)).isEqualTo(2);
    }

    @Test
    void transientFailuresAreRetriedWithBackoffUntilSentOrGivenUp() {
        // Stubbed for this test's token only: rows of other test classes in the shared database never consume the chain.
        when(pushSender.send(anyString(), eq(token()), any(), any(), any()))
                .thenReturn(SendResult.retryable("provider_timeout"))
                .thenReturn(SendResult.sent("fcm-1"));
        dispatch("order_accepted", "PA-2", "retry-" + tenant);

        parkForeignDueDeliveries();
        worker.deliverDue();
        Map<String, Object> afterFirst = myPush();
        assertThat(afterFirst).containsEntry("status", "QUEUED").containsEntry("attempts", 1).containsEntry("last_error", "provider_timeout");
        Instant nextAttempt = ((Timestamp) afterFirst.get("next_attempt_at")).toInstant();
        assertThat(nextAttempt).isAfter(Instant.now().plusSeconds(20)); // 30 s backoff after attempt 1

        worker.deliverDue(); // not due yet: untouched
        assertThat(myPush()).containsEntry("attempts", 1);

        makeDue();
        worker.deliverDue();
        Map<String, Object> sent = myPush();
        assertThat(sent).containsEntry("status", "SENT").containsEntry("attempts", 2).containsEntry("provider_message_id", "fcm-1");
        assertThat(sent.get("sent_at")).isNotNull();
        assertThat((String) sent.get("recipient_masked")).isEqualTo("****7788");

        // Give-up path: always failing, max-attempts=3.
        when(pushSender.send(anyString(), eq(token()), any(), any(), any())).thenReturn(SendResult.retryable("provider_timeout"));
        dispatch("order_delivered", "PA-3", "giveup-" + tenant);
        for (int i = 0; i < 3; i++) {
            makeDue();
            worker.deliverDue();
        }
        assertThat(jdbc.queryForMap("SELECT status, attempts FROM notification_deliveries WHERE idempotency_key = ?", "giveup-" + tenant + ":PUSH"))
                .containsEntry("status", "FAILED").containsEntry("attempts", 3);
    }

    @Test
    void aClaimSkipsRowsLockedByAnotherWorkerInsteadOfWaiting() throws Exception {
        Set<UUID> mine = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            dispatch("order_accepted", "PA-L" + i, "lock-" + i + "-" + tenant);
        }
        mine.addAll(jdbc.queryForList("SELECT id FROM notification_deliveries WHERE tenant_id = ?", UUID.class, tenant));
        assertThat(mine).hasSize(10);

        parkForeignDueDeliveries();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch claimedByA = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);
        try {
            Future<List<ClaimedDelivery>> first = pool.submit(() -> tx.execute(status -> {
                List<ClaimedDelivery> rows = store.claimDue(3, Duration.ofMinutes(2));
                claimedByA.countDown();
                await(releaseA); // keep the row locks until B has claimed
                return rows;
            }));
            assertThat(claimedByA.await(10, TimeUnit.SECONDS)).isTrue();
            // Without SKIP LOCKED this would block on A's locks until the timeout below.
            Future<List<ClaimedDelivery>> second = pool.submit(() -> tx.execute(status -> store.claimDue(1000, Duration.ofMinutes(2))));
            List<ClaimedDelivery> b = second.get(10, TimeUnit.SECONDS);
            releaseA.countDown();
            List<ClaimedDelivery> a = first.get(10, TimeUnit.SECONDS);

            Set<UUID> idsA = ids(a);
            Set<UUID> idsB = ids(b);
            assertThat(idsA).hasSize(3).doesNotContainAnyElementsOf(idsB);
            Set<UUID> union = new HashSet<>(idsA);
            union.addAll(idsB);
            assertThat(union).containsAll(mine);
        } finally {
            releaseA.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentWorkersSendEachDeliveryExactlyOnce() throws Exception {
        Map<String, AtomicInteger> sends = new ConcurrentHashMap<>();
        when(pushSender.send(anyString(), anyString(), any(), any(), any())).thenAnswer(invocation -> {
            sends.computeIfAbsent(invocation.getArgument(3), body -> new AtomicInteger()).incrementAndGet();
            Thread.sleep(50);
            return SendResult.sent("fcm-" + UUID.randomUUID());
        });
        for (int i = 0; i < 8; i++) {
            dispatch("order_ready_for_pickup", "PA-C" + i + "-" + tenant, "conc-" + i + "-" + tenant);
        }

        parkForeignDueDeliveries();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> one = pool.submit(worker::deliverDue);
            Future<?> two = pool.submit(worker::deliverDue);
            one.get(30, TimeUnit.SECONDS);
            two.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForList("SELECT status FROM notification_deliveries WHERE tenant_id = ?", String.class, tenant))
                .hasSize(8).containsOnly("SENT");
        Map<String, AtomicInteger> mineSent = new ConcurrentHashMap<>();
        sends.forEach((body, count) -> {
            if (body.contains(tenant.toString())) {
                mineSent.put(body, count);
            }
        });
        assertThat(mineSent).hasSize(8);
        assertThat(mineSent.values()).allSatisfy(count -> assertThat(count.get()).isEqualTo(1));
    }

    // --- helpers ---

    private int dispatch(String key, String reference, String dedupKey) {
        Integer written = tx.execute(status -> dispatcher.dispatch(new NotificationRequest(tenant, customer, "CUSTOMER", key, reference,
                Map.of("orderReference", reference), NotificationDispatcher.ALL_CHANNELS, dedupKey)));
        return written == null ? 0 : written;
    }

    private String token() {
        return "device-token-" + customer + "-7788";
    }

    private Map<String, Object> myPush() {
        return jdbc.queryForMap("SELECT status, attempts, last_error, next_attempt_at, provider_message_id, sent_at, recipient_masked "
                + "FROM notification_deliveries WHERE idempotency_key = ?", "retry-" + tenant + ":" + NotificationChannel.PUSH);
    }

    private void makeDue() {
        parkForeignDueDeliveries();
        jdbc.update("UPDATE notification_deliveries SET next_attempt_at = now() - interval '1 second' WHERE tenant_id = ? AND status = 'QUEUED'", tenant);
    }

    private static Set<UUID> ids(List<ClaimedDelivery> rows) {
        Set<UUID> ids = new HashSet<>();
        rows.forEach(row -> ids.add(row.id()));
        return ids;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
