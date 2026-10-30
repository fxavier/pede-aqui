package com.delivery.order.job;

import com.delivery.common.jobs.JobRunner;
import com.delivery.order.service.CheckoutQuoteStore;
import java.time.Duration;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deletes checkout quotes expired more than {@code app.jobs.checkout-quote-cleanup.grace} ago (W6-A), daily, one replica. */
@Component
@ConditionalOnProperty(prefix = "app.jobs.checkout-quote-cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CheckoutQuoteCleanupJob {
    public static final String NAME = "checkout-quote-cleanup";

    private final CheckoutQuoteStore quoteStore;
    private final JobRunner jobRunner;
    private final Duration grace;

    public CheckoutQuoteCleanupJob(CheckoutQuoteStore quoteStore, JobRunner jobRunner,
                                   @Value("${app.jobs.checkout-quote-cleanup.grace:P1D}") Duration grace) {
        this.quoteStore = quoteStore;
        this.jobRunner = jobRunner;
        this.grace = grace;
    }

    /** Scheduled tick (daily, Maputo time): purges old expired quotes. */
    @Scheduled(cron = "${app.jobs.checkout-quote-cleanup.cron:0 45 3 * * *}",
            zone = "${app.jobs.checkout-quote-cleanup.zone:Africa/Maputo}")
    @SchedulerLock(name = NAME, lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void run() {
        jobRunner.run(NAME, () -> "deleted=" + quoteStore.purgeExpired(grace));
    }
}
