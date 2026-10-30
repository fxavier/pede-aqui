package com.delivery.settlement.service;

import com.delivery.settlement.repository.LedgerRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily ledger sanity check (LAC-33): Σ debit = Σ credit per tenant and per transaction (the deferred trigger makes an
 * imbalance impossible, so any hit means the database was tampered with or the trigger was dropped), and liability
 * accounts with a debit balance (e.g. funds held going negative — a posting-model gap worth a look). Reports through
 * the {@code ledger_reconciliation_*} gauges and an ERROR/WARN log line; changes nothing.
 */
@Service
public class LedgerReconciliation {
    private static final Logger log = LoggerFactory.getLogger(LedgerReconciliation.class);

    /** Outcome of one run. */
    public record Result(List<UUID> imbalancedTenants, long unbalancedTransactions, long negativeLiabilityAccounts) {
        public boolean balanced() { return imbalancedTenants.isEmpty() && unbalancedTransactions == 0; }
    }

    private final LedgerRepository ledger;
    private final SettlementMetrics metrics;

    public LedgerReconciliation(LedgerRepository ledger, SettlementMetrics metrics) {
        this.ledger = ledger;
        this.metrics = metrics;
    }

    /** Runs the checks, publishes the gauges and logs anomalies. */
    @Transactional(readOnly = true)
    public Result reconcile() {
        List<UUID> imbalanced = ledger.imbalancedTenants();
        long unbalanced = ledger.unbalancedTransactions();
        long negative = ledger.negativeLiabilityAccounts();
        metrics.reconciliation(imbalanced.size(), unbalanced, negative);
        for (UUID tenantId : imbalanced) {
            log.error("LEDGER IMBALANCE: tenant {} entries do not balance (sum debit != sum credit)", tenantId);
        }
        if (unbalanced > 0) {
            log.error("LEDGER IMBALANCE: {} unbalanced transaction(s)", unbalanced);
        }
        if (negative > 0) {
            log.warn("Ledger: {} liability account(s) with a debit balance", negative);
        }
        return new Result(imbalanced, unbalanced, negative);
    }
}
