package com.delivery.dispatch.job;

import com.delivery.common.jobs.JobRunner;
import com.delivery.dispatch.service.CourierLocationRetention;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Schedules the courier route-history retention (LAC-40, 30 days): daily at 04:15 Africa/Maputo by default, on one
 * replica (ShedLock). See {@link CourierLocationRetention}.
 */
@Component
@ConditionalOnProperty(prefix = "app.jobs.courier-location-retention", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CourierLocationRetentionJob {
    public static final String NAME = "courier-location-retention";

    private final CourierLocationRetention retention;
    private final JobRunner jobRunner;

    public CourierLocationRetentionJob(CourierLocationRetention retention, JobRunner jobRunner) {
        this.retention = retention;
        this.jobRunner = jobRunner;
    }

    /** Scheduled tick (daily, Maputo time): deletes expired route-history rows in batches. */
    @Scheduled(cron = "${app.jobs.courier-location-retention.cron:0 15 4 * * *}",
            zone = "${app.jobs.courier-location-retention.zone:Africa/Maputo}")
    @SchedulerLock(name = NAME, lockAtMostFor = "PT1H", lockAtLeastFor = "PT1M")
    public void run() {
        jobRunner.run(NAME, () -> "deleted=" + retention.purge());
    }
}
