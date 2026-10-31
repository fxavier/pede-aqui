package com.delivery.payment.job;

import com.delivery.common.jobs.JobRunner;
import com.delivery.payment.service.PaymentAttemptResolver;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Schedules {@code payment-attempt-expiry} (LAC-32, W7-A): every minute on one replica, expires INITIATED /
 * PENDING_CUSTOMER attempts older than {@code app.payments.attempts.ttl} after a last provider query. The order stays
 * PAYMENT_PENDING (the customer may retry); the W4-A {@code payment-expiry} job still cancels unpaid orders.
 */
@Component
@ConditionalOnProperty(prefix = "app.jobs.payment-attempt-expiry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentAttemptExpiryJob {
    public static final String NAME = "payment-attempt-expiry";

    private final PaymentAttemptResolver resolver;
    private final JobRunner jobRunner;

    public PaymentAttemptExpiryJob(PaymentAttemptResolver resolver, JobRunner jobRunner) {
        this.resolver = resolver;
        this.jobRunner = jobRunner;
    }

    /** Scheduled tick: expires one batch. */
    @Scheduled(fixedDelayString = "${app.jobs.payment-attempt-expiry.interval:PT1M}",
            initialDelayString = "${app.jobs.payment-attempt-expiry.initial-delay:PT1M}")
    @SchedulerLock(name = NAME, lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
    public void run() {
        jobRunner.run(NAME, () -> resolver.expireStale().toString());
    }
}
