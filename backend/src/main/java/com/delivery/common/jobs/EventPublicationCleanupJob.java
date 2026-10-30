package com.delivery.common.jobs;

import java.time.Duration;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes completed {@code event_publication} rows older than {@code app.jobs.event-publication-cleanup.retention}
 * (default 7 days). Completed rows are kept that long only as a short audit trail of what was delivered
 * ({@code spring.modulith.events.completion-mode=update}); incomplete rows are never deleted here.
 */
@Component
@ConditionalOnProperty(prefix = "app.jobs.event-publication-cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EventPublicationCleanupJob {
    public static final String NAME = "event-publication-cleanup";

    private final CompletedEventPublications completedPublications;
    private final JobRunner jobRunner;
    private final Duration retention;

    public EventPublicationCleanupJob(CompletedEventPublications completedPublications, JobRunner jobRunner,
                                      @Value("${app.jobs.event-publication-cleanup.retention:P7D}") Duration retention) {
        this.completedPublications = completedPublications;
        this.jobRunner = jobRunner;
        this.retention = retention;
    }

    /** Scheduled tick (daily, Maputo time): purges old completed publications on one replica. */
    @Scheduled(cron = "${app.jobs.event-publication-cleanup.cron:0 30 3 * * *}",
            zone = "${app.jobs.event-publication-cleanup.zone:Africa/Maputo}")
    @SchedulerLock(name = NAME, lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void run() {
        jobRunner.run(NAME, () -> {
            completedPublications.deletePublicationsOlderThan(retention);
            return "deleted completed publications older than " + retention;
        });
    }
}
