package com.delivery.order.job;

import com.delivery.common.jobs.JobRunner;
import com.delivery.order.service.OrderTimeoutService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Schedules the vendor acceptance timeout (LAC-25, W4-A): every minute by default, on one replica (ShedLock), cancels
 * and refunds orders still PAYMENT_CONFIRMED {@code app.orders.acceptance-timeout} after payment (reason
 * VENDOR_TIMEOUT). See {@link OrderTimeoutService}.
 */
@Component
@ConditionalOnProperty(prefix = "app.jobs.acceptance-timeout", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AcceptanceTimeoutJob {
    public static final String NAME = OrderTimeoutService.ACCEPTANCE_TIMEOUT;

    private final OrderTimeoutService timeoutService;
    private final JobRunner jobRunner;

    public AcceptanceTimeoutJob(OrderTimeoutService timeoutService, JobRunner jobRunner) {
        this.timeoutService = timeoutService;
        this.jobRunner = jobRunner;
    }

    /** Scheduled tick: cancels one batch of paid orders the vendor did not accept in time. */
    @Scheduled(fixedDelayString = "${app.jobs.acceptance-timeout.interval:PT1M}",
            initialDelayString = "${app.jobs.acceptance-timeout.initial-delay:PT1M}")
    @SchedulerLock(name = NAME, lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
    public void run() {
        jobRunner.run(NAME, () -> timeoutService.cancelUnacceptedOrders().toString());
    }
}
