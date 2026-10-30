package com.delivery.settlement.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Settlement metrics (low-cardinality tags only): {@code ledger_postings_total{type,result=posted|duplicate|skipped}}
 * and the reconciliation gauges {@code ledger_reconciliation_imbalanced_tenants},
 * {@code ledger_reconciliation_unbalanced_transactions}, {@code ledger_reconciliation_negative_liability_accounts}
 * (alert when either of the first two is &gt; 0).
 */
@Component
public class SettlementMetrics {
    private final MeterRegistry registry;
    private final AtomicLong imbalancedTenants = new AtomicLong();
    private final AtomicLong unbalancedTransactions = new AtomicLong();
    private final AtomicLong negativeLiabilities = new AtomicLong();

    public SettlementMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("ledger.reconciliation.imbalanced.tenants", imbalancedTenants, AtomicLong::get).register(registry);
        Gauge.builder("ledger.reconciliation.unbalanced.transactions", unbalancedTransactions, AtomicLong::get).register(registry);
        Gauge.builder("ledger.reconciliation.negative.liability.accounts", negativeLiabilities, AtomicLong::get).register(registry);
    }

    /** Counts one posting attempt. */
    public void posting(String type, String result) {
        registry.counter("ledger.postings", "type", type, "result", result).increment();
    }

    /** Publishes the outcome of a reconciliation run. */
    public void reconciliation(long imbalanced, long unbalanced, long negative) {
        imbalancedTenants.set(imbalanced);
        unbalancedTransactions.set(unbalanced);
        negativeLiabilities.set(negative);
    }
}
