package com.delivery.common.jobs;

import java.time.Duration;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Re-delivers transactional events whose listener has not completed (LAC-47 outbox retry). An
 * {@code @ApplicationModuleListener} that throws leaves its {@code event_publication} row without a completion date;
 * this job resubmits rows older than {@code app.jobs.event-publication-retry.older-than} (default 5 min, so it never
 * races a listener that is still running). Delivery is therefore at-least-once: listeners must be idempotent.
 *
 * <p>Replaces Spring Modulith's republish-on-restart (kept off): with several replicas, a restarting instance would
 * resubmit publications another live instance is still processing; this job runs on one replica (ShedLock).
 */
@Component
@ConditionalOnProperty(prefix = "app.jobs.event-publication-retry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EventPublicationRetryJob {
    public static final String NAME = "event-publication-retry";

    private final IncompleteEventPublications incompletePublications;
    private final JobRunner jobRunner;
    private final Duration olderThan;

    public EventPublicationRetryJob(IncompleteEventPublications incompletePublications, JobRunner jobRunner,
                                    @Value("${app.jobs.event-publication-retry.older-than:PT5M}") Duration olderThan) {
        this.incompletePublications = incompletePublications;
        this.jobRunner = jobRunner;
        this.olderThan = olderThan;
    }

    /** Scheduled tick: resubmits stale incomplete publications on one replica. */
    @Scheduled(fixedDelayString = "${app.jobs.event-publication-retry.interval:PT1M}",
            initialDelayString = "${app.jobs.event-publication-retry.initial-delay:PT1M}")
    @SchedulerLock(name = NAME, lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
    public void run() {
        jobRunner.run(NAME, () -> {
            incompletePublications.resubmitIncompletePublicationsOlderThan(olderThan);
            return "resubmitted incomplete publications older than " + olderThan;
        });
    }
}
