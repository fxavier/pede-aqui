package com.delivery.notification.job;

import com.delivery.common.jobs.JobRunner;
import com.delivery.notification.service.NotificationDeliveryWorker;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Schedules {@link NotificationDeliveryWorker} (LAC-48 outbox consumer): every 15 s by default, one replica at a time
 * (ShedLock). The worker's SKIP LOCKED claim + lease would also be safe with several concurrent workers.
 */
@Component
@ConditionalOnProperty(prefix = "app.jobs.notification-delivery", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NotificationDeliveryJob {
    public static final String NAME = "notification-delivery";

    private final NotificationDeliveryWorker worker;
    private final JobRunner jobRunner;

    public NotificationDeliveryJob(NotificationDeliveryWorker worker, JobRunner jobRunner) {
        this.worker = worker;
        this.jobRunner = jobRunner;
    }

    /** Scheduled tick: sends one batch of due deliveries. */
    @Scheduled(fixedDelayString = "${app.jobs.notification-delivery.interval:PT15S}",
            initialDelayString = "${app.jobs.notification-delivery.initial-delay:PT30S}")
    @SchedulerLock(name = NAME, lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
    public void run() {
        jobRunner.run(NAME, () -> worker.deliverDue().toString());
    }
}
