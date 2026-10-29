package com.delivery.order.job;

import com.delivery.common.jobs.JobRunner;
import com.delivery.order.service.ReservationReconciler;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Schedules {@link ReservationReconciler} (LAC-23 / LAC-46): every 15 min by default, on one replica (ShedLock). */
@Component
@ConditionalOnProperty(prefix = "app.jobs.reservation-reconciler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReservationReconcilerJob {
    public static final String NAME = "reservation-reconciler";

    private final ReservationReconciler reconciler;
    private final JobRunner jobRunner;

    public ReservationReconcilerJob(ReservationReconciler reconciler, JobRunner jobRunner) {
        this.reconciler = reconciler;
        this.jobRunner = jobRunner;
    }

    /** Scheduled tick: reconciles one batch of terminal orders whose stock is still reserved. */
    @Scheduled(fixedDelayString = "${app.jobs.reservation-reconciler.interval:PT15M}",
            initialDelayString = "${app.jobs.reservation-reconciler.initial-delay:PT2M}")
    @SchedulerLock(name = NAME, lockAtMostFor = "PT14M", lockAtLeastFor = "PT30S")
    public void run() {
        jobRunner.run(NAME, () -> reconciler.reconcile().toString());
    }
}
