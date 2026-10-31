package com.delivery.privacy.job;

import com.delivery.common.jobs.JobRunner;
import com.delivery.privacy.service.RetentionCleaner;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Schedules the retention policy (LAC-63): daily at 05:15 Africa/Maputo by default, on one replica (ShedLock). See
 * {@link RetentionCleaner} for the data classes and periods ({@code app.privacy.retention.*}).
 */
@Component
@ConditionalOnProperty(prefix = "app.jobs.retention-cleaner", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RetentionCleanerJob {
    private final RetentionCleaner cleaner;
    private final JobRunner jobRunner;

    public RetentionCleanerJob(RetentionCleaner cleaner, JobRunner jobRunner) {
        this.cleaner = cleaner;
        this.jobRunner = jobRunner;
    }

    /** Scheduled tick (daily, Maputo time): purges every data class past its retention period. */
    @Scheduled(cron = "${app.jobs.retention-cleaner.cron:0 15 5 * * *}", zone = "${app.jobs.retention-cleaner.zone:Africa/Maputo}")
    @SchedulerLock(name = RetentionCleaner.JOB, lockAtMostFor = "PT2H", lockAtLeastFor = "PT1M")
    public void run() {
        jobRunner.run(RetentionCleaner.JOB, () -> "deleted=" + cleaner.purgeAll());
    }
}
