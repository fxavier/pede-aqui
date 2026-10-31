package com.delivery.common.idempotency;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC access to {@code idempotency_records} (V069). Every statement autocommits on its own: the IN_PROGRESS claim must
 * be visible to concurrent duplicates while the first request is still running.
 */
@Repository
public class IdempotencyRecordRepository {

    /** A stored key. */
    public record IdempotencyRecord(UUID id, String fingerprint, String state, Integer responseStatus, String contentType,
                                    byte[] body, Instant createdAt, Instant expiresAt) {
        boolean completed() {
            return "COMPLETED".equals(state);
        }
    }

    private final JdbcTemplate jdbc;

    public IdempotencyRecordRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Claims the key (IN_PROGRESS) unless it exists; a concurrent claimer waits on the unique index, then loses. */
    public Optional<UUID> tryClaim(UUID tenantId, String subject, String key, String fingerprint, Instant now, Instant expiresAt) {
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO idempotency_records (id, tenant_id, actor_subject, idempotency_key, request_fingerprint, state,
                                                 created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, 'IN_PROGRESS', ?, ?)
                ON CONFLICT (tenant_id, actor_subject, idempotency_key) DO NOTHING""",
                id, tenantId, subject, key, fingerprint, Timestamp.from(now), Timestamp.from(expiresAt));
        return inserted == 1 ? Optional.of(id) : Optional.empty();
    }

    /** The stored record of the key, if any. */
    public Optional<IdempotencyRecord> find(UUID tenantId, String subject, String key) {
        List<IdempotencyRecord> rows = jdbc.query("""
                SELECT id, request_fingerprint, state, response_status, response_content_type, response_body, created_at, expires_at
                FROM idempotency_records WHERE tenant_id = ? AND actor_subject = ? AND idempotency_key = ?""",
                (rs, i) -> new IdempotencyRecord(rs.getObject("id", UUID.class), rs.getString("request_fingerprint"), rs.getString("state"),
                        (Integer) rs.getObject("response_status"), rs.getString("response_content_type"), rs.getBytes("response_body"),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("expires_at").toInstant()),
                tenantId, subject, key);
        return rows.stream().findFirst();
    }

    /** Stores the 2xx response of a claimed key. */
    public void complete(UUID id, int status, String contentType, byte[] body) {
        jdbc.update("UPDATE idempotency_records SET state = 'COMPLETED', response_status = ?, response_content_type = ?, response_body = ? WHERE id = ?",
                status, contentType, body, id);
    }

    /** Releases a claimed key (non-2xx outcome or failure), so the client may retry with it. */
    public void release(UUID id) {
        jdbc.update("DELETE FROM idempotency_records WHERE id = ? AND state = 'IN_PROGRESS'", id);
    }

    /** Deletes a stale record (expired, or an abandoned IN_PROGRESS) by id — a re-claimed key has a new id. */
    public boolean deleteStale(UUID id) {
        return jdbc.update("DELETE FROM idempotency_records WHERE id = ?", id) == 1;
    }

    /** Deletes up to {@code limit} expired records; returns the count. */
    public int deleteExpired(Instant now, int limit) {
        return jdbc.update("""
                DELETE FROM idempotency_records WHERE id IN (
                    SELECT id FROM idempotency_records WHERE expires_at < ? ORDER BY expires_at LIMIT ?)""",
                Timestamp.from(now), limit);
    }
}
