package com.delivery.common.idempotency;

import com.delivery.common.jobs.JobRunner;
import java.time.Instant;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Schedules {@code idempotency-cleanup} (LAC-24, W7-A): daily, deletes expired {@code idempotency_records} in batches. */
@Component
@ConditionalOnProperty(prefix = "app.jobs.idempotency-cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IdempotencyCleanupJob {
    public static final String NAME = "idempotency-cleanup";

    private final IdempotencyRecordRepository repository;
    private final IdempotencyProperties properties;
    private final JobRunner jobRunner;

    public IdempotencyCleanupJob(IdempotencyRecordRepository repository, IdempotencyProperties properties, JobRunner jobRunner) {
        this.repository = repository;
        this.properties = properties;
        this.jobRunner = jobRunner;
    }

    /** Scheduled tick. */
    @Scheduled(cron = "${app.jobs.idempotency-cleanup.cron:0 15 3 * * *}", zone = "${app.jobs.idempotency-cleanup.zone:Africa/Maputo}")
    @SchedulerLock(name = NAME, lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void run() {
        jobRunner.run(NAME, () -> "deleted=" + purgeExpired());
    }

    /** Deletes expired records (bounded number of batches); returns the count. */
    public int purgeExpired() {
        int total = 0;
        Instant now = Instant.now();
        for (int batch = 0; batch < properties.getCleanupMaxBatches(); batch++) {
            int deleted = repository.deleteExpired(now, properties.getCleanupBatchSize());
            total += deleted;
            if (deleted < properties.getCleanupBatchSize()) {
                break;
            }
        }
        return total;
    }
}
