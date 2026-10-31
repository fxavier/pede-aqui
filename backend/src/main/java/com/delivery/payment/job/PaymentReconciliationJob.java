package com.delivery.payment.job;

import com.delivery.common.jobs.JobRunner;
import com.delivery.payment.service.PaymentReconciliationService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Schedules {@code payment-reconciliation} (LAC-32, W7-A): daily (03:00 Africa/Maputo by default) on one replica,
 * compares SUCCEEDED attempts with captured payments and opens {@code payment_reconciliation_cases} for mismatches.
 */
@Component
@ConditionalOnProperty(prefix = "app.jobs.payment-reconciliation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentReconciliationJob {
    public static final String NAME = "payment-reconciliation";

    private final PaymentReconciliationService reconciliation;
    private final JobRunner jobRunner;

    public PaymentReconciliationJob(PaymentReconciliationService reconciliation, JobRunner jobRunner) {
        this.reconciliation = reconciliation;
        this.jobRunner = jobRunner;
    }

    /** Scheduled tick: one reconciliation run. */
    @Scheduled(cron = "${app.jobs.payment-reconciliation.cron:0 0 3 * * *}", zone = "${app.jobs.payment-reconciliation.zone:Africa/Maputo}")
    @SchedulerLock(name = NAME, lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void run() {
        jobRunner.run(NAME, () -> reconciliation.runDaily().toString());
    }
}
