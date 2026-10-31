package com.delivery.payment.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC access to {@code payment_webhook_events} (V068): the dedup insert needs {@code ON CONFLICT … WHERE signature_valid},
 * which has no JPA equivalent. Rows hold ids, statuses and the payload SHA-256 only — never the payload.
 */
@Repository
public class PaymentWebhookEventRepository {
    private final JdbcTemplate jdbc;

    public PaymentWebhookEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Claims a verified event: inserts it unless the same (provider, event id) was already recorded with a valid
     * signature. A concurrent duplicate blocks on the unique index until the first transaction ends, then sees the
     * conflict. Returns the new row id, or null for a duplicate.
     */
    public UUID claimVerified(String provider, String eventId, String payloadSha256, Instant receivedAt) {
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO payment_webhook_events (id, provider, provider_event_id, signature_valid, payload_sha256, outcome,
                                                    received_at)
                VALUES (?, ?, ?, TRUE, ?, 'IGNORED', ?)
                ON CONFLICT (provider, provider_event_id) WHERE signature_valid DO NOTHING""",
                id, provider, eventId, payloadSha256, Timestamp.from(receivedAt));
        return inserted == 1 ? id : null;
    }

    /** Stores what the claimed event did, in the transaction that applied it. */
    public void markProcessed(UUID id, String outcome, UUID tenantId, UUID attemptId, String providerReference,
                              String reportedStatus, Instant processedAt) {
        jdbc.update("""
                UPDATE payment_webhook_events SET outcome = ?, tenant_id = ?, attempt_id = ?, provider_reference = ?,
                       reported_status = ?, processed_at = ?
                WHERE id = ?""",
                outcome, tenantId, attemptId, truncate(providerReference, 128), reportedStatus, Timestamp.from(processedAt), id);
    }

    /** Forensic row for a callback whose signature did not verify (never deduplicated, never processed). */
    public void recordInvalid(String provider, String claimedEventId, String payloadSha256, Instant receivedAt) {
        jdbc.update("""
                INSERT INTO payment_webhook_events (id, provider, provider_event_id, signature_valid, payload_sha256, outcome,
                                                    received_at)
                VALUES (?, ?, ?, FALSE, ?, 'INVALID_SIGNATURE', ?)""",
                UUID.randomUUID(), provider, truncate(claimedEventId == null ? "unknown" : claimedEventId, 128), payloadSha256,
                Timestamp.from(receivedAt));
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
