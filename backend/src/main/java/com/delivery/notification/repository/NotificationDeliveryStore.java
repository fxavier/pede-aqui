package com.delivery.notification.repository;

import com.delivery.notification.api.NotificationChannel;
import com.delivery.notification.entity.NotificationDelivery.Status;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC write side of {@code notification_deliveries} (V067, LAC-48): idempotent insert, the job's claim with
 * {@code FOR UPDATE SKIP LOCKED} + lease, and the conditional result update. Kept in SQL on purpose so the outbox
 * protocol (who may move a row, and when) is visible in one place.
 */
@Repository
public class NotificationDeliveryStore {
    private final JdbcTemplate jdbc;

    public NotificationDeliveryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** A new delivery row. {@code nextAttemptAt} = now for QUEUED rows; terminal rows use it only as a timestamp. */
    public record NewDelivery(UUID tenantId, UUID recipientProfileId, NotificationChannel channel, String templateKey,
                              int templateVersion, String locale, String title, String body, String businessReference,
                              Status status, String lastError, String idempotencyKey) {}

    /** The fields the sender needs for one claimed row. */
    public record ClaimedDelivery(UUID id, UUID tenantId, UUID recipientProfileId, NotificationChannel channel,
                                  String templateKey, String title, String body, int attempts) {}

    /**
     * Inserts the row unless its idempotency key exists (then nothing happens). Returns the new id, or empty for a
     * duplicate dispatch. Runs in the caller's transaction (the business transaction = outbox write).
     */
    public Optional<UUID> insertIfAbsent(NewDelivery delivery) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        boolean terminalSent = delivery.status() == Status.SENT;
        int rows = jdbc.update("""
                        INSERT INTO notification_deliveries (id, tenant_id, recipient_profile_id, channel, template_key, template_version,
                            locale, title, body, business_reference, status, attempts, next_attempt_at, last_error, idempotency_key,
                            created_at, updated_at, sent_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (idempotency_key) DO NOTHING""",
                id, delivery.tenantId(), delivery.recipientProfileId(), delivery.channel().name(), delivery.templateKey(),
                delivery.templateVersion(), delivery.locale(), delivery.title(), delivery.body(), delivery.businessReference(),
                delivery.status().name(), terminalSent ? 1 : 0, now, delivery.lastError(), delivery.idempotencyKey(), now, now,
                terminalSent ? now : null);
        return rows == 1 ? Optional.of(id) : Optional.empty();
    }

    /**
     * Claims up to {@code limit} due QUEUED rows, oldest due first, in one statement: rows locked by another worker are
     * skipped ({@code SKIP LOCKED}), each claimed row gets {@code attempts + 1} and a lease ({@code next_attempt_at =
     * now + lease}) so no other worker picks it up while it is being sent. A worker that dies mid-send leaves the row
     * QUEUED; it becomes due again when the lease expires (at-least-once). Must run in its own short transaction.
     */
    public List<ClaimedDelivery> claimDue(int limit, Duration lease) {
        return jdbc.query("""
                        UPDATE notification_deliveries d
                           SET attempts = d.attempts + 1,
                               next_attempt_at = now() + make_interval(secs => ?),
                               updated_at = now()
                         WHERE d.id IN (SELECT id FROM notification_deliveries
                                         WHERE status = 'QUEUED' AND next_attempt_at <= now()
                                         ORDER BY next_attempt_at, id
                                         LIMIT ?
                                         FOR UPDATE SKIP LOCKED)
                        RETURNING d.id, d.tenant_id, d.recipient_profile_id, d.channel, d.template_key, d.title, d.body, d.attempts""",
                (rs, rowNum) -> new ClaimedDelivery(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                        rs.getObject("recipient_profile_id", UUID.class), NotificationChannel.valueOf(rs.getString("channel")),
                        rs.getString("template_key"), rs.getString("title"), rs.getString("body"), rs.getInt("attempts")),
                (double) lease.toSeconds(), limit);
    }

    /**
     * Records the result of the attempt that claimed the row with {@code claimedAttempts}. Conditional on the row
     * still being QUEUED with that attempt count, so a late worker whose lease expired cannot overwrite a newer
     * attempt's result. {@code retryIn} is the backoff for a row that stays QUEUED (null for terminal statuses).
     * Returns false when the row moved on.
     */
    public boolean recordResult(UUID id, int claimedAttempts, Status status, Duration retryIn, String lastError,
                                String providerMessageId, String recipientMasked) {
        // Database time throughout (like the claim's lease), so replicas with skewed clocks agree on "due".
        return jdbc.update("""
                        UPDATE notification_deliveries
                           SET status = ?, next_attempt_at = now() + make_interval(secs => ?), last_error = ?,
                               provider_message_id = ?, recipient_masked = COALESCE(?, recipient_masked), updated_at = now(),
                               sent_at = CASE WHEN ? = 'SENT' THEN now() ELSE sent_at END
                         WHERE id = ? AND status = 'QUEUED' AND attempts = ?""",
                status.name(), retryIn == null ? 0d : (double) retryIn.toSeconds(), lastError, providerMessageId,
                recipientMasked, status.name(), id, claimedAttempts) == 1;
    }
}
