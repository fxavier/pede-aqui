package com.delivery.privacy.service;

import com.delivery.privacy.config.PrivacyProperties;
import com.delivery.privacy.config.PrivacyProperties.ClassPolicy;
import com.delivery.privacy.repository.RetentionRunLog;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Applies the retention policy (LAC-63, W7-C; job {@code retention-cleaner}): for every enabled
 * {@link RetentionDataClass}, deletes rows older than {@code now - retention} in bounded batches, each batch in its own
 * short transaction (no long locks; a failure keeps what earlier batches deleted). Append-only tables are purged only
 * through their sanctioned escape — {@code SET LOCAL app.audit_logs_purge = 'on'} (V053) or
 * {@code SET LOCAL app.status_history_purge = 'on'} (V058) inside the batch transaction; nothing else ever sets them.
 * One {@code data_retention_runs} row per class per run; metrics {@code retention_rows_deleted_total{data_class}} and
 * {@code retention_class_runs_total{data_class,result}}. A failing class is logged and counted and does not stop the
 * other classes.
 */
@Service
public class RetentionCleaner {
    public static final String JOB = "retention-cleaner";
    private static final Logger log = LoggerFactory.getLogger(RetentionCleaner.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final PrivacyProperties properties;
    private final RetentionRunLog runLog;
    private final MeterRegistry meterRegistry;
    private Clock clock = Clock.systemUTC();

    public RetentionCleaner(JdbcTemplate jdbc, TransactionTemplate tx, PrivacyProperties properties, RetentionRunLog runLog,
                            MeterRegistry meterRegistry) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.properties = properties;
        this.runLog = runLog;
        this.meterRegistry = meterRegistry;
    }

    /** Test seam for a fixed clock. */
    @Autowired(required = false)
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    /** Purges every enabled data class; returns rows deleted per class (-1 = failed) as a PII-free summary. */
    public Map<String, Long> purgeAll() {
        Map<String, Long> deleted = new LinkedHashMap<>();
        for (RetentionDataClass dataClass : RetentionDataClass.values()) {
            deleted.put(dataClass.key(), purge(dataClass));
        }
        return deleted;
    }

    /** Purges one data class; returns the rows deleted, 0 when disabled, -1 when it failed. */
    public long purge(RetentionDataClass dataClass) {
        ClassPolicy policy = properties.getRetention().policyOf(dataClass);
        Instant startedAt = clock.instant();
        if (!policy.isEnabled() || policy.getRetention() == null || policy.getRetention().isZero() || policy.getRetention().isNegative()) {
            runLog.record(JOB, dataClass.key(), policy.getRetention(), null, 0, "SKIPPED", "disabled", startedAt, clock.instant());
            classRun(dataClass, "skipped");
            return 0;
        }
        Instant cutoff = startedAt.minus(policy.getRetention());
        int batchSize = Math.max(1, properties.getRetention().getBatchSize());
        int maxBatches = Math.max(1, properties.getRetention().getMaxBatchesPerClass());
        long total = 0;
        try {
            for (int batch = 0; batch < maxBatches; batch++) {
                Integer rows = tx.execute(status -> {
                    if (dataClass.purgeFlag() != null) {
                        jdbc.execute("SET LOCAL " + dataClass.purgeFlag() + " = 'on'");
                    }
                    return jdbc.update(dataClass.batchDeleteSql(), Timestamp.from(cutoff), batchSize);
                });
                int deleted = rows == null ? 0 : rows;
                total += deleted;
                if (deleted < batchSize) {
                    break;
                }
            }
            meterRegistry.counter("retention.rows.deleted", "data_class", dataClass.key()).increment(total);
            runLog.record(JOB, dataClass.key(), policy.getRetention(), cutoff, total, "SUCCESS", null, startedAt, clock.instant());
            classRun(dataClass, "success");
            return total;
        } catch (RuntimeException ex) {
            log.error("Retention purge of {} failed after {} rows", dataClass.key(), total, ex);
            meterRegistry.counter("retention.rows.deleted", "data_class", dataClass.key()).increment(total);
            runLog.record(JOB, dataClass.key(), policy.getRetention(), cutoff, total, "FAILURE", ex.getClass().getSimpleName(),
                    startedAt, clock.instant());
            classRun(dataClass, "failure");
            return -1;
        }
    }

    private void classRun(RetentionDataClass dataClass, String result) {
        meterRegistry.counter("retention.class.runs", "data_class", dataClass.key(), "result", result).increment();
    }
}
