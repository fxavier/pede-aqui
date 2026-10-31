package com.delivery.payment.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * PSP metrics (LAC-32, low-cardinality tags only): {@code payment_attempts_total{provider,status}} (attempt created /
 * resolved), {@code payment_webhooks_total{provider,result=processed|ignored|duplicate|unmatched|invalid_signature}},
 * {@code payment_reconciliation_cases_opened_total{type}} and the gauge {@code payment_reconciliation_cases_open}
 * (alert when it stays &gt; 0 for more than a day).
 */
@Component
public class PaymentMetrics {
    private final MeterRegistry registry;
    private final AtomicLong openCases = new AtomicLong();

    public PaymentMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("payment.reconciliation.cases.open", openCases, AtomicLong::get).register(registry);
    }

    /** An attempt reached {@code status} (INITIATED on creation, then each transition). */
    public void attempt(String provider, String status) {
        registry.counter("payment.attempts", "provider", provider, "status", status).increment();
    }

    /** One webhook delivery and what it did. */
    public void webhook(String provider, String result) {
        registry.counter("payment.webhooks", "provider", provider, "result", result).increment();
    }

    /** A new reconciliation case. */
    public void caseOpened(String type) {
        registry.counter("payment.reconciliation.cases.opened", "type", type).increment();
    }

    /** Open cases after a reconciliation run. */
    public void openCases(long count) {
        openCases.set(count);
    }
}
