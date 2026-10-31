package com.delivery.privacy.job;

import com.delivery.common.jobs.JobRunner;
import com.delivery.privacy.service.AccountErasureService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Schedules the pseudonymisation of accounts whose deletion reflection period ended (LAC-19): hourly by default, on
 * one replica (ShedLock). See {@link AccountErasureService}.
 */
@Component
@ConditionalOnProperty(prefix = "app.jobs.account-deletion", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AccountDeletionJob {
    private final AccountErasureService erasureService;
    private final JobRunner jobRunner;

    public AccountDeletionJob(AccountErasureService erasureService, JobRunner jobRunner) {
        this.erasureService = erasureService;
        this.jobRunner = jobRunner;
    }

    /** Scheduled tick: processes due deletion requests (bounded batch, oldest first). */
    @Scheduled(cron = "${app.jobs.account-deletion.cron:0 20 * * * *}", zone = "${app.jobs.account-deletion.zone:Africa/Maputo}")
    @SchedulerLock(name = AccountErasureService.JOB, lockAtMostFor = "PT50M", lockAtLeastFor = "PT1M")
    public void run() {
        jobRunner.run(AccountErasureService.JOB, erasureService::processDue);
    }
}
