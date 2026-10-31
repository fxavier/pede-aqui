package com.delivery.privacy.repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Appends rows to {@code data_retention_runs} (V071): one per data class per retention/erasure job run, no personal data. */
@Repository
public class RetentionRunLog {
    private final JdbcTemplate jdbc;

    public RetentionRunLog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Records one data-class outcome of a job run (autocommit: survives a later failure of the run). */
    public void record(String job, String dataClass, Duration retention, Instant cutoff, long rowsAffected, String result,
                       String errorCode, Instant startedAt, Instant finishedAt) {
        jdbc.update("""
                INSERT INTO data_retention_runs (id, job, job_run_id, data_class, retention, cutoff, rows_affected, result,
                                                 error_code, started_at, finished_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                UUID.randomUUID(), job, MDC.get("jobRunId"), dataClass, retention == null ? null : retention.toString(),
                cutoff == null ? null : Timestamp.from(cutoff), rowsAffected, result, truncate(errorCode),
                Timestamp.from(startedAt), Timestamp.from(finishedAt));
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 120 ? value : value.substring(0, 120);
    }
}
