package com.delivery.order.job;

import com.delivery.common.jobs.JobRunner;
import com.delivery.order.service.OrderTimeoutService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Schedules the payment expiry (LAC-25, W4-A): every minute by default, on one replica (ShedLock), cancels orders
 * still PAYMENT_PENDING after {@code app.orders.payment-timeout} (reason PAYMENT_EXPIRED). See {@link OrderTimeoutService}.
 */
@Component
@ConditionalOnProperty(prefix = "app.jobs.payment-expiry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentExpiryJob {
    public static final String NAME = OrderTimeoutService.PAYMENT_EXPIRY;

    private final OrderTimeoutService timeoutService;
    private final JobRunner jobRunner;

    public PaymentExpiryJob(OrderTimeoutService timeoutService, JobRunner jobRunner) {
        this.timeoutService = timeoutService;
        this.jobRunner = jobRunner;
    }

    /** Scheduled tick: expires one batch of overdue pending payments. */
    @Scheduled(fixedDelayString = "${app.jobs.payment-expiry.interval:PT1M}",
            initialDelayString = "${app.jobs.payment-expiry.initial-delay:PT1M}")
    @SchedulerLock(name = NAME, lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
    public void run() {
        jobRunner.run(NAME, () -> timeoutService.expirePendingPayments().toString());
    }
}
