package com.delivery.payment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Binds {@code app.payments.attempts.*}: limits, polling backoff, expiry and reconciliation windows (LAC-31/32). */
@Component
@ConfigurationProperties(prefix = "app.payments.attempts")
public class PaymentAttemptProperties {
    /** Attempts a payment may have in total (retries after FAILED/EXPIRED included). */
    private int maxPerPayment = 5;
    /** INITIATED/PENDING_CUSTOMER attempts older than this are expired (after a last status query). */
    private Duration ttl = Duration.ofMinutes(3);
    /** First status query this long after the attempt is created; then exponential backoff. */
    private Duration pollAfter = Duration.ofSeconds(60);
    /** Backoff base (delay = base × 2^polls) and cap. */
    private Duration pollBackoffBase = Duration.ofSeconds(15);
    private Duration pollMaxBackoff = Duration.ofMinutes(30);
    /** UNKNOWN for longer than this → reconciliation case ATTEMPT_UNRESOLVED (manual review, LAC-32). */
    private Duration unknownReviewAfter = Duration.ofMinutes(30);
    /** Items per poller/expiry run. */
    private int batchSize = 100;
    /** How far back the daily reconciliation looks. */
    private Duration reconciliationLookback = Duration.ofDays(3);

    public int getMaxPerPayment() { return maxPerPayment; }
    public void setMaxPerPayment(int maxPerPayment) { this.maxPerPayment = maxPerPayment; }
    public Duration getTtl() { return ttl; }
    public void setTtl(Duration ttl) { this.ttl = ttl; }
    public Duration getPollAfter() { return pollAfter; }
    public void setPollAfter(Duration pollAfter) { this.pollAfter = pollAfter; }
    public Duration getPollBackoffBase() { return pollBackoffBase; }
    public void setPollBackoffBase(Duration pollBackoffBase) { this.pollBackoffBase = pollBackoffBase; }
    public Duration getPollMaxBackoff() { return pollMaxBackoff; }
    public void setPollMaxBackoff(Duration pollMaxBackoff) { this.pollMaxBackoff = pollMaxBackoff; }
    public Duration getUnknownReviewAfter() { return unknownReviewAfter; }
    public void setUnknownReviewAfter(Duration unknownReviewAfter) { this.unknownReviewAfter = unknownReviewAfter; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public Duration getReconciliationLookback() { return reconciliationLookback; }
    public void setReconciliationLookback(Duration reconciliationLookback) { this.reconciliationLookback = reconciliationLookback; }
}
