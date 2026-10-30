package com.delivery.settlement.job;

import com.delivery.common.jobs.JobRunner;
import com.delivery.settlement.service.LedgerReconciliation;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Schedules the ledger sanity check (LAC-33): daily at 04:45 Africa/Maputo by default, on one replica (ShedLock).
 * See {@link LedgerReconciliation}.
 */
@Component
@ConditionalOnProperty(prefix = "app.jobs.ledger-reconciliation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LedgerReconciliationJob {
    public static final String NAME = "ledger-reconciliation";

    private final LedgerReconciliation reconciliation;
    private final JobRunner jobRunner;

    public LedgerReconciliationJob(LedgerReconciliation reconciliation, JobRunner jobRunner) {
        this.reconciliation = reconciliation;
        this.jobRunner = jobRunner;
    }

    /** Scheduled tick (daily, Maputo time): checks the ledger balances on one replica. */
    @Scheduled(cron = "${app.jobs.ledger-reconciliation.cron:0 45 4 * * *}",
            zone = "${app.jobs.ledger-reconciliation.zone:Africa/Maputo}")
    @SchedulerLock(name = NAME, lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void run() {
        jobRunner.run(NAME, () -> {
            LedgerReconciliation.Result result = reconciliation.reconcile();
            return "balanced=" + result.balanced() + " imbalancedTenants=" + result.imbalancedTenants().size()
                    + " unbalancedTransactions=" + result.unbalancedTransactions()
                    + " negativeLiabilityAccounts=" + result.negativeLiabilityAccounts();
        });
    }
}
