package com.delivery.notification.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds {@code app.notifications.*} (LAC-48): template locale, the delivery job's batch/lease/retry policy and the
 * per-user device cap. Provider selection ({@code app.notifications.<channel>.provider}) is read by the sender beans.
 */
@Component
@ConfigurationProperties(prefix = "app.notifications")
public class NotificationProperties {
    /** Template locale (only pt-PT is seeded). */
    private String locale = "pt-PT";
    /** Rows claimed per job tick. */
    private int batchSize = 100;
    /** Attempts before a row becomes FAILED (the first send counts). */
    private int maxAttempts = 5;
    /** Backoff after attempt n = baseBackoff × 2^(n-1), capped at maxBackoff. */
    private Duration baseBackoff = Duration.ofSeconds(30);
    private Duration maxBackoff = Duration.ofHours(1);
    /** How long a claimed row is invisible to other workers (must exceed the slowest provider call). */
    private Duration lease = Duration.ofMinutes(2);
    /** Push devices notified per user (most recently seen first). */
    private int maxDevicesPerUser = 10;

    /** Delay before the next attempt after {@code attempts} failed attempts (exponential, capped). */
    public Duration backoffAfter(int attempts) {
        int exponent = Math.max(0, Math.min(attempts - 1, 20));
        Duration delay = baseBackoff.multipliedBy(1L << exponent);
        return delay.compareTo(maxBackoff) > 0 ? maxBackoff : delay;
    }

    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public Duration getBaseBackoff() { return baseBackoff; }
    public void setBaseBackoff(Duration baseBackoff) { this.baseBackoff = baseBackoff; }
    public Duration getMaxBackoff() { return maxBackoff; }
    public void setMaxBackoff(Duration maxBackoff) { this.maxBackoff = maxBackoff; }
    public Duration getLease() { return lease; }
    public void setLease(Duration lease) { this.lease = lease; }
    public int getMaxDevicesPerUser() { return maxDevicesPerUser; }
    public void setMaxDevicesPerUser(int maxDevicesPerUser) { this.maxDevicesPerUser = maxDevicesPerUser; }
}
