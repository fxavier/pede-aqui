package com.delivery.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.delivery.common.PostgresApiE2ETestBase;
import com.delivery.common.TestParents;
import com.delivery.privacy.config.PrivacyProperties;
import com.delivery.privacy.service.RetentionCleaner;
import com.delivery.privacy.service.RetentionDataClass;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * LAC-63 retention cleaner on real Postgres, per data class: rows past the configured period are deleted in batches,
 * newer rows and rows still in use (QUEUED deliveries, in-flight provisioning) are kept; the append-only tables
 * (audit_logs V053, order_status_history V058) are purged only through their transaction-local purge flags (a plain
 * DELETE still fails); every class writes a data_retention_runs row and the per-class metric; a disabled class is
 * skipped.
 */
class RetentionCleanerPostgresTest extends PostgresApiE2ETestBase {
    private static final Duration OLD = Duration.ofDays(4000);

    @Autowired private RetentionCleaner cleaner;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PrivacyProperties properties;
    @Autowired private MeterRegistry meterRegistry;

    private UUID tenant;
    private UUID profile;

    @BeforeEach
    void seed() {
        tenant = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', 'MZN', now(), now())",
                tenant, "Retention " + tenant, "retention-" + tenant);
        profile = TestParents.customer(jdbc, tenant);
    }

    @Test
    void auditLogsArePurgedOnlyThroughTheirFlagAndOnlyWhenExpired() {
        UUID old = audit(ago(Duration.ofDays(731)));
        UUID recent = audit(ago(Duration.ofDays(700)));
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_logs WHERE id = ?", old)).rootCause().hasMessageContaining("append-only");

        double metricBefore = deletedMetric("audit-logs");
        long deleted = cleaner.purge(RetentionDataClass.AUDIT_LOGS);

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(exists("audit_logs", old)).isFalse();
        assertThat(exists("audit_logs", recent)).isTrue();
        assertThat(deletedMetric("audit-logs") - metricBefore).isEqualTo(deleted);
        Map<String, Object> run = lastRun("audit-logs");
        assertThat(run).containsEntry("result", "SUCCESS").containsEntry("retention", "PT17520H").containsEntry("job", "retention-cleaner");
        assertThat(((Number) run.get("rows_affected")).longValue()).isEqualTo(deleted);
        // The flag is transaction-local: right after the purge a plain DELETE is refused again.
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_logs WHERE id = ?", recent)).rootCause().hasMessageContaining("append-only");
    }

    @Test
    void orderStatusHistoryIsPurgedThroughTheStatusHistoryFlag() {
        UUID order = order();
        UUID old = history(order, ago(Duration.ofDays(3651)));
        UUID recent = history(order, ago(Duration.ofDays(10)));
        assertThatThrownBy(() -> jdbc.update("DELETE FROM order_status_history WHERE id = ?", old)).rootCause().hasMessageContaining("append-only");

        assertThat(cleaner.purge(RetentionDataClass.ORDER_STATUS_HISTORY)).isGreaterThanOrEqualTo(1);
        assertThat(exists("order_status_history", old)).isFalse();
        assertThat(exists("order_status_history", recent)).isTrue();
    }

    @Test
    void notificationDeliveriesKeepQueuedRowsAndNotificationsAreDeletedInBatches() {
        UUID oldSent = delivery("SENT", ago(Duration.ofDays(181)));
        UUID oldQueued = delivery("QUEUED", ago(Duration.ofDays(400)));
        UUID recentSent = delivery("SENT", ago(Duration.ofDays(30)));
        assertThat(cleaner.purge(RetentionDataClass.NOTIFICATION_DELIVERIES)).isGreaterThanOrEqualTo(1);
        assertThat(exists("notification_deliveries", oldSent)).isFalse();
        assertThat(exists("notification_deliveries", oldQueued)).isTrue();
        assertThat(exists("notification_deliveries", recentSent)).isTrue();

        int originalBatch = properties.getRetention().getBatchSize();
        properties.getRetention().setBatchSize(2);
        try {
            for (int i = 0; i < 5; i++) inbox(ago(Duration.ofDays(200)));
            UUID recent = inbox(ago(Duration.ofDays(1)));
            assertThat(cleaner.purge(RetentionDataClass.NOTIFICATIONS)).isGreaterThanOrEqualTo(5);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE tenant_id = ?", Long.class, tenant)).isEqualTo(1);
            assertThat(exists("notifications", recent)).isTrue();
        } finally {
            properties.getRetention().setBatchSize(originalBatch);
        }
    }

    @Test
    void provisioningRequestsKeepInFlightRows() {
        UUID done = provisioning("SUCCEEDED", ago(Duration.ofDays(2)));
        UUID failed = provisioning("FAILED", ago(Duration.ofDays(2)));
        UUID inFlight = provisioning("PENDING", ago(Duration.ofDays(2)));
        UUID fresh = provisioning("SUCCEEDED", ago(Duration.ofHours(1)));
        cleaner.purge(RetentionDataClass.PROVISIONING_REQUESTS);
        assertThat(exists("provisioning_requests", done)).isFalse();
        assertThat(exists("provisioning_requests", failed)).isFalse();
        assertThat(exists("provisioning_requests", inFlight)).isTrue();
        assertThat(exists("provisioning_requests", fresh)).isTrue();
    }

    @Test
    void aDisabledClassIsSkippedAndPurgeAllCoversEveryClass() {
        UUID old = inbox(ago(OLD));
        PrivacyProperties.ClassPolicy disabled = new PrivacyProperties.ClassPolicy();
        disabled.setEnabled(false);
        properties.getRetention().getClasses().put("notifications", disabled);
        try {
            Map<String, Long> result = cleaner.purgeAll();
            assertThat(result).containsOnlyKeys(java.util.Arrays.stream(RetentionDataClass.values()).map(RetentionDataClass::key).toList());
            assertThat(result.values()).allSatisfy(rows -> assertThat(rows).isGreaterThanOrEqualTo(0));
            assertThat(result).containsEntry("notifications", 0L);
            assertThat(exists("notifications", old)).isTrue();
            assertThat(lastRun("notifications")).containsEntry("result", "SKIPPED").containsEntry("error_code", "disabled");
        } finally {
            properties.getRetention().getClasses().remove("notifications");
        }
        cleaner.purge(RetentionDataClass.NOTIFICATIONS);
        assertThat(exists("notifications", old)).isFalse();
    }

    // --- helpers ---

    private static Timestamp ago(Duration duration) {
        return Timestamp.from(Instant.now().minus(duration));
    }

    private UUID audit(Timestamp at) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO audit_logs (id, tenant_id, actor_user_id, action, target_type, target_id, business_reference, result, created_at) "
                + "VALUES (?, ?, 'retention-test', 'TEST_ACTION', 'test', ?, NULL, 'SUCCESS', ?)", id, tenant, id.toString(), at);
        return id;
    }

    private UUID order() {
        UUID id = UUID.randomUUID();
        UUID vendor = TestParents.vendor(jdbc, tenant);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, status, subtotal, fees, taxes, discounts, discount_total,
                                    total, checkout_idempotency_key, delivery_confirmation_code_hash, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'DELIVERED', 10, 0, 0, 0, 0, 10, ?, 'hash', ?, ?)""",
                id, tenant, "RET-" + id.toString().substring(0, 8), profile, vendor, "ret-" + id, now, now);
        return id;
    }

    private UUID history(UUID orderId, Timestamp at) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO order_status_history (id, tenant_id, order_id, from_status, to_status, actor_type, actor_id, reason, correlation_id, created_at) "
                + "VALUES (?, ?, ?, 'DELIVERING', 'DELIVERED', 'SYSTEM', 'test', NULL, NULL, ?)", id, tenant, orderId, at);
        return id;
    }

    private UUID delivery(String status, Timestamp at) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO notification_deliveries (id, tenant_id, recipient_profile_id, channel, template_key, template_version, locale, title, body,
                    business_reference, status, attempts, next_attempt_at, recipient_masked, provider_message_id, idempotency_key, created_at, updated_at, sent_at)
                VALUES (?, ?, ?, 'PUSH', 'order_accepted', 1, 'pt-PT', NULL, 'corpo', NULL, ?, 1, ?, '****1234', NULL, ?, ?, ?, NULL)""",
                id, tenant, profile, status, at, "retention-" + id, at, at);
        return id;
    }

    private UUID inbox(Timestamp at) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO notifications (id, tenant_id, recipient_user_id, recipient_role, type, title, message, created_at) "
                + "VALUES (?, ?, ?, 'CUSTOMER', 'test', 't', 'm', ?)", id, tenant, profile, at);
        return id;
    }

    private UUID provisioning(String status, Timestamp at) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO provisioning_requests (id, idempotency_key, owner_email, external_ref, tenant_id, status, response_json, created_at, updated_at) "
                + "VALUES (?, ?, 'owner@example.test', NULL, NULL, ?, NULL, ?, ?)", id, "ret-" + id, status, at, at);
        return id;
    }

    private boolean exists(String table, UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM " + table + " WHERE id = ?)", Boolean.class, id));
    }

    private Map<String, Object> lastRun(String dataClass) {
        return jdbc.queryForMap("SELECT * FROM data_retention_runs WHERE data_class = ? ORDER BY started_at DESC, finished_at DESC LIMIT 1", dataClass);
    }

    private double deletedMetric(String dataClass) {
        var counter = meterRegistry.find("retention.rows.deleted").tag("data_class", dataClass).counter();
        return counter == null ? 0 : counter.count();
    }
}
