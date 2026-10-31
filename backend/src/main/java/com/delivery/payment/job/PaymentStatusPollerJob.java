package com.delivery.payment.job;

import com.delivery.common.jobs.JobRunner;
import com.delivery.payment.service.PaymentAttemptResolver;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Schedules {@code payment-status-poller} (LAC-32, W7-A): every 30 s on one replica, queries the provider for open
 * payment attempts whose next poll is due (see {@link PaymentAttemptResolver}).
 */
@Component
@ConditionalOnProperty(prefix = "app.jobs.payment-status-poller", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentStatusPollerJob {
    public static final String NAME = "payment-status-poller";

    private final PaymentAttemptResolver resolver;
    private final JobRunner jobRunner;

    public PaymentStatusPollerJob(PaymentAttemptResolver resolver, JobRunner jobRunner) {
        this.resolver = resolver;
        this.jobRunner = jobRunner;
    }

    /** Scheduled tick: polls one batch. */
    @Scheduled(fixedDelayString = "${app.jobs.payment-status-poller.interval:PT30S}",
            initialDelayString = "${app.jobs.payment-status-poller.initial-delay:PT1M}")
    @SchedulerLock(name = NAME, lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
    public void run() {
        jobRunner.run(NAME, () -> resolver.pollDue().toString());
    }
}
