package com.delivery.dispatch.job;

import com.delivery.common.jobs.JobRunner;
import com.delivery.dispatch.service.CourierPresenceSweeper;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Schedules the courier presence sweeper (LAC-42): every minute by default, on one replica (ShedLock). See
 * {@link CourierPresenceSweeper} for what it changes and {@code app.courier-presence.*} for its settings.
 */
@Component
@ConditionalOnProperty(prefix = "app.jobs.courier-presence-sweeper", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CourierPresenceSweeperJob {
    public static final String NAME = "courier-presence-sweeper";

    private final CourierPresenceSweeper sweeper;
    private final JobRunner jobRunner;

    public CourierPresenceSweeperJob(CourierPresenceSweeper sweeper, JobRunner jobRunner) {
        this.sweeper = sweeper;
        this.jobRunner = jobRunner;
    }

    /** Scheduled tick: heals BUSY couriers without a delivery and (if enforced) takes silent AVAILABLE couriers offline. */
    @Scheduled(fixedDelayString = "${app.jobs.courier-presence-sweeper.interval:PT1M}",
            initialDelayString = "${app.jobs.courier-presence-sweeper.initial-delay:PT1M}")
    @SchedulerLock(name = NAME, lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
    public void run() {
        jobRunner.run(NAME, () -> sweeper.sweep().toString());
    }
}
